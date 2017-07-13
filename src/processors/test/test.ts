import { SafetyJim, MessageProcessor } from '../../safetyjim/safetyjim';
import { Message } from 'discord.js';

class Test implements MessageProcessor {
    // tslint:disable-next-line:no-empty
    constructor() { }

    public async onMessage(bot: SafetyJim, msg: Message): Promise<void> {
        bot.log.info(msg.content);
        return;
    }
}

export = Test;
