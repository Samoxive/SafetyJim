import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Tags } from '../../database/models/Tags';

class Tag implements Command {
    public usage = [
        'tag list - Shows all tags and responses to user',
        'tag <name> - Responds with reponse of the given tag',
        'tag add <name> <response> - Adds a tag with the given name and response',
        'tag edit <name> <response> - Changes response of tag with given name',
        'tag remove <name> - Deletes tag with the given name',
    ];

    private subcommands = ['list', 'add', 'edit', 'remove'];
    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (splitArgs[0] === 'list') {
            await this.displayTags(bot, msg);
            return;
        } else if (splitArgs[0] === 'add') {
            await this.addTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '));
            return;
        } else if (splitArgs[0] === 'edit') {
            await this.editTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '));
            return;
        } else if (splitArgs[0] === 'remove') {
           await this.deleteTag(bot, msg, splitArgs[1]);
           return;
        } else {
            if (!splitArgs[0]) {
                await bot.failReact(msg);
                return true;
            }

            let response = await Tags.find<Tags>({
                where: {
                    guildid: msg.guild.id,
                    name: splitArgs[0],
                },
            });

            if (!response) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, 'Could not find a tag with that name!');
                return;
            }

            await bot.successReact(msg);
            await bot.sendMessage(msg.channel, response.response);
            return;
        }

     }

    private async displayTags(bot: SafetyJim, msg: Discord.Message) {
        let tags = await Tags.findAll<Tags>({
            where: {
                guildid: msg.guild.id,
            },
        });

        if (tags.length === 0) {
            await bot.successReact(msg);
            await bot.sendMessage(msg.channel, `No tags have been added yet!`);
            return;
        }

        let embed = {
            author: { name: 'Safety Jim', icon_url: bot.client.user.avatarURL },
            fields: [{ name: 'List of tags', value: tags
                .map((tag) => `\`${tag.name}\` - ${tag.response}`)
                .join('\n').trim() }],
            color: 0x4286f4,
        };

        await bot.successReact(msg);
        await bot.sendMessage(msg.channel, { embed });
        return;
    }

    private async addTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string) {
        if (this.subcommands.includes(name)) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, `You can't create a tag with the same name as a subcommand!`);
            return;
        }
        if (!msg.member.hasPermission('ADMINISTRATOR')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'You don\'t have enough permissions to use this command!');
            return;
        }

        if (name === undefined || name === '') {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'Please give a tag and a response to add!');
            return;
        }

        if (response === undefined || response === '') {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'Empty responses aren\'t allowed!');
            return;
        }

        try {
            await Tags.create<Tags>({
                guildid: msg.guild.id,
                name,
                response,
            });

            await bot.successReact(msg);
        } catch (e) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, `Tag "${name}" already exists!`);
        }
    }

    private async editTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string) {
        if (!msg.member.hasPermission('ADMINISTRATOR')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'You don\'t have enough permissions to use this command!');
            return;
        }

        if (name === undefined || name === '') {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'Please give a tag and a response to edit!');
            return;
        }

        if (response === undefined || response === '') {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'Empty responses are not allowed!');
            return;
        }

        let dbResponse = await Tags.find<Tags>({
            where: {
                guildid: msg.guild.id,
                name,
            },
        });

        if (!dbResponse) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, `Tag ${name} does not exist!`);
            return;
        }

        await Tags.update({ response }, {
            where: {
                guildid: msg.guild.id,
                name,
            },
        });

        await bot.successReact(msg);
        return;
    }

    private async deleteTag(bot: SafetyJim, msg: Discord.Message, name: string) {
        if (!msg.member.hasPermission('ADMINISTRATOR')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'You don\'t have enough permissions to use this command!');
            return;
        }

        if (name === undefined) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'Remove command requires an argument!');
            return;
        }

        let dbResponse = await Tags.find<Tags>({
            where: {
                guildid: msg.guild.id,
                name,
            },
        });

        if (!dbResponse) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, `Tag ${name} does not exist!`);
            return;
        }

        await Tags.destroy({
            where: {
                guildid: msg.guild.id,
                name,
            },
        });

        await bot.successReact(msg);
    }
}

export = Tag;
