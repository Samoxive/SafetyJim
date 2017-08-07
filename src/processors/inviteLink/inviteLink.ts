import * as parseUri from 'parse-uri';

import { SafetyJim, MessageProcessor } from '../../safetyjim/safetyjim';
import { Message, MessageReaction, User, PermissionString } from 'discord.js';
import { Settings } from '../../database/models/Settings';

class InviteLink implements MessageProcessor {
    private blacklistedHosts: string[];
    private shortestHostLength: number;
    private whitelistedRoles: PermissionString[];

    constructor() {
        this.blacklistedHosts = [ 'discord.gg' ];
        this.shortestHostLength = 10;
        this.whitelistedRoles = [
            'ADMINISTRATOR',
            'BAN_MEMBERS',
            'KICK_MEMBERS',
            'MANAGE_ROLES',
            'MANAGE_MESSAGES',
        ];
    }

    public async onMessage(bot: SafetyJim, msg: Message): Promise<boolean> {
        if (msg.member == null) {
            return;
        }

        for (let role of this.whitelistedRoles) {
            if (msg.member.hasPermission(role)) {
                return;
            }
        }

        let enabled = await bot.database.getGuildSetting(msg.guild, 'invitelinkremover');

        if (enabled === 'false') {
            return;
        }

        let result = msg.content.split(' ')
                               .filter((word) => word.length >= this.shortestHostLength)
                               .map((word) => (parseUri(word)).host as string)
                               .filter((host) => this.isBlackListed(host));

        if (result.length === 0) {
            return;
        }

        try {
            await msg.delete();
            await msg.channel.send(`I'm sorry ${msg.author}, you can't send invite links here.`);
        } finally {
            return true;
        }
    }

    private isBlackListed(host: string): boolean {
        for (let blacklisted of this.blacklistedHosts) {
            if (host === blacklisted) {
                return true;
            }
        }

        return false;
    }
}

export = InviteLink;
