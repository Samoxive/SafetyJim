import { Logger, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class SwearFilter implements Logger {
    public isUsed = true;

    // Let's start small
    private swears = ["damn"]

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) { }
    
    public async onMessage(bot: SafetyJim, msg: Discord.Message) {
        let words = msg.toString().split(' ');
        words.filter( (word) => {
            if(this.swears.includes(word)) {
                bot.log.info(`User ${msg.author.username.toString()} has sworn!`);
            }
        } )
    }
}

export = SwearFilter;