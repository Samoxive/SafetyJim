import { SafetyJim, MessageProcessor } from '../../safetyjim/safetyjim';
import { Message } from 'discord.js';

class Test implements MessageProcessor {
    // tslint:disable-next-line:no-empty
    constructor() { }

    public async onMessage(bot: SafetyJim, msg: Message): Promise<void> {
        bot.log.info(`Message said: ${msg.content}`);
        return;
    }

    public async onMessageDelete(bot: SafetyJim, msg: Message): Promise<void> {
        bot.log.info(`Message deleted: ${msg.content}`);
        return;
    }
}

export = Test;
