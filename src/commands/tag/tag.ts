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

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');
        if (!args || !['say', 'list', 'add', 'edit', 'remove'].includes(splitArgs[0])) {
            return true;
        }
        let guild = msg.guild;
        if (splitArgs[0] === 'say') {
            if (splitArgs[1] === undefined) {
                msg.channel.send('Command requires a tag to say!');
                bot.failReact(msg);
                return;
            }
            bot.database.getTagResponse(splitArgs[1], guild).then((response) => {
                if (response === undefined) {
                    msg.channel.send(`No tag with name "${splitArgs[1]}"!`);
                    bot.failReact(msg);
                    return;
                }
                msg.channel.send(response);
                bot.successReact(msg);
                return;
            });
        }

        if (splitArgs[0] === 'list') {
            bot.successReact(msg);
            this.displayTags(bot, msg);
            return;
        }

        if (splitArgs[0] === 'add') {
            // Check if adding the tag passed or failed
            this.addTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '));
            return;
        }

        if (splitArgs[0] === 'edit') {
            this.editTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '));
            return;
        }
        if (splitArgs[0] === 'remove') {
           this.deleteTag(bot, msg, splitArgs[1]);
           return;
        }

        return;
     }

    private displayTags(bot: SafetyJim, msg: Discord.Message) {
        bot.log.info(`Sending ${msg.author.username} list of tags`);

        bot.database.getAllTags(msg.guild).then((tags) => {
            if (tags === undefined) {
                msg.author.send('No tags have been added yet!');
                return;
            }

            msg.author.send('', {
                embed: {
                    title: 'SafetyJim - Tags',
                    description: tags
                        .map((tag) => `\` ${tag.TagName}\` - ${tag.TagResponse}`)
                        .join('\n'),
                },
            });
            return;
        });
    }

    private addTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string) {
        if (response === undefined || response === '') {
            msg.channel.send('Empty responses aren\'t allowed!');
            bot.failReact(msg);
            return;
        }

        bot.database.getTagResponse(name, msg.guild)
        .then((_) => {
            msg.channel.send(`Tag ${name} already exists!`);
            bot.failReact(msg);
        })
        .catch((err) => {
            bot.database.createTag(name, response, msg.guild);
            bot.successReact(msg);
        });

    }

    private editTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string) {
        if (response === undefined || response === '') {
            msg.channel.send('Empty responses are not allowed!');
            bot.failReact(msg);
        }

        bot.database.getTagResponse(name, msg.guild).then((dbResponse) => {
            if (dbResponse === undefined) {
                msg.channel.send(`Tag ${name} does not exist!`);
                bot.failReact(msg);
            } else {
                bot.database.updateTagResponse(name, response, msg.guild);
                bot.successReact(msg);
            }
        });
    }

    private deleteTag(bot: SafetyJim, msg: Discord.Message, name: string) {
        if (name === undefined) {
            msg.channel.send('Remove commands requires argument!');
            bot.failReact(msg);
            return;
        }

        bot.database.getTagResponse(name, msg.guild).then((dbResponse) => {
            if (dbResponse === undefined) {
                msg.channel.send(`Tag ${name} does not exist!`);
                bot.failReact(msg);
            } else {
                bot.database.delTagResponse(name, msg.guild);
                bot.successReact(msg);
            }
        });
    }
}

export = Tag;
