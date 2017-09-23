import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';

class Help implements Command {
    public usage = 'help - lists all the available commands and their usage';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let prefix = await jim.database.getGuildSetting(msg.guild, 'prefix');
        await Utils.successReact(msg);
        await Utils.sendMessage(msg.channel, { embed: {
            author: { name: 'Safety Jim - Commands', icon_url: shard.client.user.avatarURL },
            description: shard.getUsageStrings(prefix),
            color: 0x4286f4,
        } });

        return;
    }
}

export = Help;
