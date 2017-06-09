import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Help implements Command {
    public usage = 'help - lists all the available commands and their usage';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        bot.database.getGuildPrefix(msg.guild)
           .then((prefix) => {
                bot.successReact(msg);
                msg.channel.send('', { embed: {
                    author: { name: 'Safety Jim - Commands', icon_url: bot.client.user.avatarURL },
                    description: bot.getUsageStrings(prefix),
                    color: 0x4286f4,
                }});
            });
        return;
    }
}

export = Help;
