import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Tag implements Command {
    public usage = [
        'tag say <name> - Responds with reponse of the given tag',
        'tag list - DMs all tags and responses to user',
        'tag add <name> <response> - Adds a tag with the given name and response',
        'tag edit <name> <response> - Changes response of tag with given name',
        'tag remove <name> - Deletes tag with the given name',
    ];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        if (!args || !['say', 'list', 'add', 'edit', 'remove'].includes(splitArgs[0])) {
            return true;
        }
        let guild = msg.guild;
        if (splitArgs[0] === 'say') {
            if (splitArgs[1] === undefined) {
                await msg.channel.send('Command requires a tag to say!');
                await bot.failReact(msg);
                return;
            }

            await bot.database.getTagResponse(splitArgs[1], guild)
            .then((response) => {
                msg.channel.send(response);
                bot.successReact(msg);
                return;
            })
            .catch((error) => {
                    msg.channel.send(`No tag with name "${splitArgs[1]}"!`);
                    bot.failReact(msg);
                    return;
                });
        }

        if (splitArgs[0] === 'list') {
            await this.displayTags(bot, msg);
            return;
        }

        if (splitArgs[0] === 'add') {
            await this.addTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '));
            return;
        }

        if (splitArgs[0] === 'edit') {
            await this.editTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '));
            return;
        }
        if (splitArgs[0] === 'remove') {
           await this.deleteTag(bot, msg, splitArgs[1]);
           return;
        }

        return;
     }

    private async displayTags(bot: SafetyJim, msg: Discord.Message) {
        await bot.database.getAllTags(msg.guild)
        .then((tags) => {
            if (tags.length === 0) {
                msg.author.send(`No tags have been added yet!`);
                bot.successReact(msg);
                return;
            }

            msg.author.send('', {
                embed: {
                    title: 'SafetyJim - Tags',
                    description: tags
                        .map((tag) => `"${tag.TagName}" - ${tag.TagResponse}`)
                        .join('\n'),
                },
            });
            bot.successReact(msg);
            return;
        });
    }

    private async addTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string) {
        if (response === undefined || response === '') {
            await msg.channel.send('Empty responses aren\'t allowed!');
            await bot.failReact(msg);
            return;
        }

        // If the tag doesn't exist, we shouldn't be able to find it in the DB
        await bot.database.getTagResponse(name, msg.guild)
        .then((_) => {
            msg.channel.send(`Tag ${name} already exists!`);
            bot.failReact(msg);
        })
        .catch((err) => {
            bot.database.createTag(name, response, msg.guild);
            msg.channel.send(`Created tag with name "${name}"`);
            bot.successReact(msg);
        });

    }

    private async editTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string) {
        if (response === undefined || response === '') {
            await msg.channel.send('Empty responses are not allowed!');
            await bot.failReact(msg);
            return;
        }

        await bot.database.getTagResponse(name, msg.guild)
        .then((dbResponse) => {
            bot.database.updateTagResponse(name, response, msg.guild);
            msg.channel.send(`Edited tag ${name}!`);
            bot.successReact(msg);
        })
        .catch((error) => {
            msg.channel.send(`Tag ${name} does not exist!`);
            bot.failReact(msg);
        });
    }

    private async deleteTag(bot: SafetyJim, msg: Discord.Message, name: string) {
        if (name === undefined) {
            await msg.channel.send('Remove command requires argument!');
            await bot.failReact(msg);
            return;
        }

        await bot.database.getTagResponse(name, msg.guild)
        .then((dbResponse) => {
            bot.database.delTagResponse(name, msg.guild);
            msg.channel.send(`Deleted tag ${name}!`);
            bot.successReact(msg);
        })
        .catch((error) => {
            msg.channel.send(`Tag ${name} does not exist!`);
            bot.failReact(msg);
        });
    }
}

export = Tag;
