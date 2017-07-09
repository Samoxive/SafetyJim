import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Help implements Command {
    public usage = 'help - lists all the available commands and their usage';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let prefix = await bot.database.getSetting(msg.guild, 'Prefix');
        await bot.successReact(msg);
        await msg.channel.send({ embed: {
            author: { name: 'Safety Jim - Commands', icon_url: bot.client.user.avatarURL },
            description: bot.getUsageStrings(prefix),
            color: 0x4286f4,
        } });

        return;
    }
}

export = Help;
