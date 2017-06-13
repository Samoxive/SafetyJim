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

    // Temporary. TODO: Replace with DB
    private tags = {
        test: 'this here is a test',
    };

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');
        if (!args || !['say', 'list', 'add', 'edit', 'remove'].includes(splitArgs[0])) {
            return true;
        }

        if (splitArgs[0] === 'say') {
            if (splitArgs[1] === undefined) {
                msg.channel.send('Command requires a tag to say!');
                bot.failReact(msg);
                return;
            }
            if (this.tags[splitArgs[1]] === undefined) {
                msg.channel.send(`No tag with name "${splitArgs[1]}"!`);
                bot.failReact(msg);
                return;
            }
            msg.channel.send(this.tags[splitArgs[1]]);
            bot.successReact(msg);
            return;
        }

        if (splitArgs[0] === 'list') {
            bot.successReact(msg);
            this.displayTags(bot, msg);
            return;
        }

        if (splitArgs[0] === 'add') {
            // Check if adding the tag passed or failed
            if (this.addTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '))) {
                bot.successReact(msg);
            } else {
                bot.failReact(msg);
            }
            return;
        }

        if (splitArgs[0] === 'edit') {
            if (this.editTag(bot, msg, splitArgs[1], splitArgs.slice(2).join(' '))) {
                bot.successReact(msg);
            } else {
                bot.failReact(msg);
            }
            return;
        }

        if (splitArgs[0] === 'remove') {
            if (this.deleteTag(bot, msg, splitArgs[1])) {
                bot.successReact(msg);
            } else {
                bot.failReact(msg);
            }
            return;
        }

        return;
     }

    private displayTags(bot: SafetyJim, msg: Discord.Message) {
        bot.log.info(`Sending ${msg.author.username} list of tags`);

        if (Object.keys(this.tags).length === 0) {
            msg.author.send('No tags have been added yet!');
            return;
        }

        msg.author.send('', {
            embed: {
                title: 'SafetyJim - Tags',
                description: Object.keys(this.tags)
                    .map((tag) => `\` ${tag}\` - ${this.tags[tag]}`)
                    .join('\n'),
            },
        });
        return;
    }

    private addTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string): boolean {
        if (this.tags[name] !== undefined) {
            msg.channel.send(`Tag "${name}" already exists!`);
            return false;
        }

        if (response === undefined || response === '') {
            msg.channel.send('Empty responses aren\'t allowed!');
            return false;
        }
        this.tags[name] = response;
        return true;
    }

    private editTag(bot: SafetyJim, msg: Discord.Message, name: string, response: string): boolean {
        if (this.tags[name] === undefined) {
            msg.channel.send(`Tag "${name}" does not exist!`);
            return false;
        }
        if (response === undefined || response === '') {
            msg.channel.send('Empty responses aren\'t allowed!');
            return false;
        }
        this.tags[name] = response;
        return true;
    }

    private deleteTag(bot: SafetyJim, msg: Discord.Message, name: string): boolean {
        if (name === undefined) {
            msg.channel.send('Remove commands requires argument!');
            return false;
        }
        if (this.tags[name] === undefined) {
            msg.channel.send(`Tag "${name}" does not exist!`);
            return false;
        }
        delete this.tags[name];
        return true;
    }
}

export = Tag;
