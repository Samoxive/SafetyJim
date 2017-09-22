import { Sequelize } from 'sequelize-typescript';
import { Config } from '../config/config';
import * as path from 'path';
import { LoggerInstance } from 'winston';
import { Guild } from 'discord.js';
import { Settings } from './models/Settings';
import { SafetyJim } from '../safetyjim/safetyjim';
import { Shard } from '../safetyjim/shard';

export const defaultWelcomeMessage = 'Welcome to $guild $user!';

export type SettingKey = 'modlogactive' |
                         'modlogchannelid' |
                         'holdingroomroleid' |
                         'holdingroomactive' |
                         'invitelinkremover' |
                         'holdingroomminutes' |
                         'welcomemessagechannelid' |
                         'prefix' |
                         'welcomemessage' |
                         'welcomemessageactive' |
                         'silentcommands';

export class BotDatabase {
    public database: Sequelize;

    constructor(private config: Config, private log: LoggerInstance) {
        let databaseConfig = config.database;
        this.database = new Sequelize({
            name: databaseConfig.name,
            dialect: 'postgres',
            username: databaseConfig.user,
            password: databaseConfig.pass,
            host: databaseConfig.host,
            port: databaseConfig.port,
            modelPaths: [path.join(__dirname, 'models')],
            logging: false,
        });
    }

    public async init(): Promise<BotDatabase> {
        try {
            await this.database.authenticate();
        } catch (err) {
            this.log.error(`Failed to connect to postgresql database, terminating... ${err.stack}`);
        }

        try {
            await this.database.sync();
        } catch (e) {
            throw e;
        }

        return this;
    }

    public async getGuildSetting(guild: Guild, key: SettingKey): Promise<string> {
        return (await Settings.find<Settings>({
            where: {
                guildid: guild.id,
                key,
            },
        })).value;
    }

    public async getGuildSettings(guild: Guild): Promise<Map<SettingKey, string>> {
        let settings = await Settings.findAll<Settings>({
            where: {
                guildid: guild.id,
            },
        });

        let result = new Map<SettingKey, string>();

        for (let setting of settings) {
            result.set(setting.key as SettingKey, setting.value);
        }

        return result;
    }

    public async getValuesOfKey(key: SettingKey): Promise<Map<string, string>> {
        let result = new Map<string, string>();
        let rows = await Settings.findAll<Settings>({
            where: {
                key,
            },
        });

        for (let row of rows) {
            result.set(row.guildid, row.value);
        }

        return result;
    }

    public async updateSetting(guild: Guild, key: SettingKey, value: string): Promise<void> {
        await Settings.update<Settings>({ value }, {
            where: {
                guildid: guild.id,
                key,
            },
        });
    }

    public async createGuildSettings(bot: Shard, guild: Guild): Promise<void> {
        await this.createKeyValueSetting(guild, 'silentcommands', 'false');
        await this.createKeyValueSetting(guild, 'invitelinkremover', 'false');
        await this.createKeyValueSetting(guild, 'modlogactive', 'false');
        await this.createKeyValueSetting(guild, 'modlogchannelid', bot.getDefaultChannel(guild).id);
        await this.createKeyValueSetting(guild, 'holdingroomroleid', null);
        await this.createKeyValueSetting(guild, 'holdingroomactive', 'false');
        await this.createKeyValueSetting(guild, 'holdingroomminutes', '3');
        await this.createKeyValueSetting(guild, 'prefix', this.config.jim.default_prefix);
        await this.createKeyValueSetting(guild, 'welcomemessageactive', 'false');
        await this.createKeyValueSetting(guild, 'welcomemessage', defaultWelcomeMessage);
        await this.createKeyValueSetting(guild, 'welcomemessagechannelid', bot.getDefaultChannel(guild).id);
    }

    private async createKeyValueSetting(guild: Guild, key: SettingKey, value: string): Promise<void> {
        await Settings.create<Settings>({ guildid: guild.id, key, value });
    }
}

export let possibleKeys = ['modlogactive',
                           'modlogchannelid',
                           'holdingroomroleid',
                           'holdingroomactive',
                           'invitelinkremover',
                           'holdingroomminutes',
                           'welcomemessagechannelid',
                           'prefix',
                           'welcomemessage',
                           'welcomemessageactive',
                           'silentcommands'];
