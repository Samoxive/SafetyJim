import { Config } from '../config/config';
import * as winston from 'winston';
import * as Discord from 'discord.js';
import * as cron from 'cron';
import * as snekfetch from 'snekfetch';
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase, possibleKeys } from '../database/database';
import { Shard } from './shard';
import * as Utils from './utils';
import { Joins } from '../database/models/Joins';
import { Bans } from '../database/models/Bans';
import { Settings } from '../database/models/Settings';
import { Mutes } from '../database/models/Mutes';
import { CommandLogs } from '../database/models/CommandLogs';
import { Reminders } from '../database/models/Reminders';
import { Metrics } from '../metrics/metrics';

const DiscordBotsGuildID = '110373943822540800';
const DiscordBotListGuildID = '264445053596991498';
const NovoGuildID = '297462937646530562';

type RegexRecords = { string: RegExp };
type Commands = { string: Command };

export interface Command {
    usage: string | string[];
    run: (shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string) => Promise<boolean>;
}

export interface MessageProcessor {
    onMessage?: (shard: Shard, jim: SafetyJim, msg: Discord.Message) => Promise<boolean>;
    onMessageDelete?: (shard: Shard, jim: SafetyJim, msg: Discord.Message) => Promise<void>;
    onReaction?: (shard: Shard, jim: SafetyJim, reaction: Discord.MessageReaction, user: Discord.User) => Promise<void>;
    onReactionDelete?: (shard: Shard, jim: SafetyJim,
                        reaction: Discord.MessageReaction, user: Discord.User) => Promise<void>;
}

export class SafetyJim {
    public bootTime: Date;
    private clients: Shard[];
    private allowUsersCronJob;
    private unbanUserCronJob;
    private unmuteUserCronJob;
    private remindRemindersCronJob;
    private metricsCronJob;
    private metrics: Metrics;

    constructor(public config: Config,
                public database: BotDatabase,
                public log: winston.LoggerInstance) {
        this.bootTime = new Date();

        Settings.findAll<Settings>({
            where: {
                key: 'prefix',
            },
        }).then((prefixes) => prefixes.map((prefix) => ({ guildid: prefix.guildid, prefix: prefix.value })))
          .then((prefixes) => {
            for (let i = 0; i < this.config.jim.shard_count; i++) {
                this.clients.push(new Shard(i, this, this.database, this.config, this.log, this.metrics, prefixes));
            }
        });
    }

    public getGuildCount(): number {
        return this.clients.reduce((acc, elem) => acc + elem.client.guilds.size, 0);
    }

    public async updateDiscordBotLists(): Promise<void> {
        let count = this.getGuildCount();
        this.metrics.gauge('guild.count', count);
        if (!this.config.botlist.enabled) {
            return;
        }

        for (let list of this.config.botlist.list) {
            try {
                // TODO(sam): replace snekfetch at some point, it is a less active
                // undocumented library
                await snekfetch
                        .post(list.url.replace('$id', this.clients[0].client.user.id))
                        .set('Authorization', list.token)
                        .send({ server_count: count })
                        .then();
            } catch (err) {
                if (!list.ignore_errors) {
                    this.log.error(`Updating ${list.name} failed with error ${err}`);
                }
            }
        }
    }

    private loadProcessors(): void {
        let processorsFolderPath = path.join(__dirname, '..', 'processors');
        if (!fs.existsSync(processorsFolderPath) || !fs.statSync(processorsFolderPath).isDirectory()) {
            this.log.error('Processors directory could not be found!');
            process.exit(1);
        }

        let processorList = fs.readdirSync(processorsFolderPath);

        for (let processor of processorList) {
            if (!fs.statSync(path.join(processorsFolderPath, processor)).isDirectory()) {
                this.log.warn(`Found file "${processor}", ignoring...`);
            } else {
                try {
                    let proc: MessageProcessor = require(path.join(processorsFolderPath, processor, processor + '.js'));
                    this.clients.forEach((client) => client.loadProcessor(new proc(this)));
                    this.log.info(`Loaded processor "${processor}"`);
                } catch (e) {
                    this.log.warn(`Could not load processor "${processor}"!`);
                }
            }
        }
    }

    private loadCommands(): void {
        let commandsFolderPath = path.join(__dirname, '..', 'commands');
        if (!fs.existsSync(commandsFolderPath) || !fs.statSync(commandsFolderPath).isDirectory()) {
            this.log.error('Commands directory could not be found!');
            process.exit(1);
        }

        let commandList = fs.readdirSync(commandsFolderPath);

        for (let command of commandList) {
            if (!fs.statSync(path.join(commandsFolderPath, command)).isDirectory()) {
                this.log.warn(`Found file "${command}", ignoring...`);
            } else {
                try {
                    let cmd = require(path.join(commandsFolderPath, command, command + '.js')) as Command;
                    this.clients.forEach((client) => client.loadCommand(new cmd(this), command));
                    this.log.info(`Loaded command "${command}"`);
                } catch (e) {
                    this.log.warn(`Could not load command "${command}"! ${e.stack}`);
                }
            }
        }
    }

    private isBotFarm(guild: Discord.Guild) {
        return (guild.id !== DiscordBotListGuildID) &&
               (guild.id !== DiscordBotsGuildID) &&
               (guild.id !== NovoGuildID) &&
               (guild.members.filter((member) => member.user.bot).size > 20);
    }

    private async allowUsers(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let usersToBeAllowed = await Joins.findAll<Joins>({
            where: {
                allowed: false,
                allowtime: {
                    $lt: now,
                },
            },
        });

        for (let user of usersToBeAllowed) {
            let shardId = Utils.findShardIdFromGuildId(user.guildid, this.config.jim.shard_count);
            let dGuild = this.clients[shardId].client.guilds.get(user.guildid);

            if (dGuild == null) {
                await Joins.update<Joins>({ allowed: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
                continue;
            }

            let enabled = await this.database.getGuildSetting(dGuild, 'holdingroomactive');

            if (enabled === 'true') {
                await this.clients[shardId].client.fetchUser(user.userid, true);
                let dUser = await dGuild.fetchMember(user.userid);
                let roleID = await this.database.getGuildSetting(dGuild, 'holdingroomroleid');
                try {
                    await dUser.addRole(roleID);
                    this.log.info(`Allowed "${dUser.user.tag}" in guild "${dGuild.name}".`);
                } catch (e) {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not allow user ${dUser.user.tag} (${dUser.id}) in guild ${dGuild.id} : ${JSON.stringify(e)}`);
                    await Utils.sendMessage(Utils.getDefaultChannel(dGuild), 'I could not allow a user into the server from the holding room. I probably don\'t have permissions!');
                } finally {
                    await Joins.update({ allowed: true }, {
                        where: {
                            userid: user.userid,
                            guildid: user.guildid,
                        },
                    });
                }
            }
        }
    }

    private async unbanUsers(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let usersToBeUnbanned = await Bans.findAll<Bans>({
            where: {
                unbanned: false,
                expires: true,
                expiretime: {
                    $lt: now,
                },
            },
        });

        if (usersToBeUnbanned == null) {
            return;
        }

        for (let user of usersToBeUnbanned) {
            let shardId = Utils.findShardIdFromGuildId(user.guildid, this.config.jim.shard_count);
            let g = this.clients[shardId].client.guilds.get(user.guildid);

            if (g == null) {
                Bans.update<Bans>({ unbanned: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
            } else {
                let unbanUser = await this.clients[shardId].client.fetchUser(user.userid);
                try {
                    await g.unban(unbanUser);
                    this.log.info(`Unbanned "${unbanUser.tag}" in guild "${g.name}".`);
                } catch (e) {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not unban user ${unbanUser.tag} (${unbanUser.id}) in guild ${g.id} : ${JSON.stringify(e)}`);
                    await Utils.sendMessage(Utils.getDefaultChannel(g), 'I could not unban a user that was previously temporarily banned. I probably don\'t have permissions!');
                } finally {
                    Bans.update({ unbanned: true }, {
                        where: {
                            userid: user.userid,
                            guildid: user.guildid,
                        },
                    });
                }
            }
        }
    }

    private async unmuteUsers(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let usersToBeUnmuted = await Mutes.findAll<Mutes>({
            where: {
                unmuted: false,
                expires: true,
                expiretime: {
                    $lt: now,
                },
            },
        });

        for (let user of usersToBeUnmuted) {
            let shardId = Utils.findShardIdFromGuildId(user.guildid, this.config.jim.shard_count);
            let guild = this.clients[shardId].client.guilds.get(user.guildid);

            if (!guild || !guild.roles.find('name', 'Muted')) {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
                return;
            }

            await this.clients[shardId].client.fetchUser(user.userid, true);
            let member: Discord.GuildMember;

            try {
                await this.clients[shardId].client.fetchUser(user.userid);
                member = await guild.fetchMember(user.userid);
            } catch (e) {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
            }

            if (!member) {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
                return;
            }
            try {
                await member.removeRole(guild.roles.find('name', 'Muted'));
            } catch (e) {
                // tslint:disable-next-line:max-line-length
                this.log.warn(`Could not unmute user ${member.user.tag} (${member.id}) in guild ${guild.id} : ${JSON.stringify(e)}`);
                await Utils.sendMessage(Utils.getDefaultChannel(guild), 'I could not unban a user that was previously temporarily banned. I probably don\'t have permissions!');
            } finally {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
            }
        }
    }

    private async remindReminders(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let reminders = await Reminders.findAll<Reminders>({
            where: {
                reminded: false,
                remindtime: {
                    $lt: now,
                },
            },
        });

        for (let reminder of reminders) {
            let shardId = Utils.findShardIdFromGuildId(reminder.guildid, this.config.jim.shard_count);
            let user = await this.clients[shardId].client.fetchUser(reminder.userid, true);
            let guild = this.clients[shardId].client.guilds.get(reminder.guildid);

            if (guild == null) {
                await Reminders.update({ reminded: true }, {
                    where: {
                        id: reminder.id,
                    },
                });
                continue;
            }

            let channel = guild.channels.get(reminder.channelid) as Discord.TextChannel;
            let member = await guild.fetchMember(user);

            if (member == null) {
                await Reminders.update({ reminded: true }, {
                    where: {
                        id: reminder.id,
                    },
                });
                continue;
            }

            let embed: Discord.RichEmbedOptions = {
                title: `Reminder - #${reminder.id}`,
                description: reminder.message,
                author: {
                    name: 'Safety Jim',
                    icon_url: this.clients[0].client.user.avatarURL,
                },
                footer: { text: 'Reminder set on' },
                timestamp: (new Date(reminder.createtime * 1000)),
                color: 0x4286f4,
            };

            try {
                await user.send({ embed });
            } catch (e) {
                if (channel != null) {
                    try {
                        await channel.send(user, { embed });
                    } catch (e) {
                        try {
                            await Utils.getDefaultChannel(guild).send(user, { embed });
                        } catch (e) {
                            //
                        }
                    }
                }
            } finally {
                await Reminders.update({ reminded: true }, {
                    where: {
                        id: reminder.id,
                    },
                });
            }
        }
    }

    private updateMetrics(): void {
        this.metrics.gauge('guild.count', this.getGuildCount());
        this.metrics.gauge('memoryUsage', process.memoryUsage().rss / (1024 * 1024));
        this.metrics.gauge('uptime', Math.floor(process.uptime() / (60 * 60)));
    }
}
