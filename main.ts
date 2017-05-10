import * as Discord from 'discord.js';
import * as log from 'winston';

const client = new Discord.Client();

client.on('ready', () => {
    log.info(`Client is ready, username: ${client.user.username}.`);
});

client.on('message', (msg) => {
    if (msg.content === 'ping') {
        msg.channel.sendMessage('pong');
    }
});

client.login('MjExNDMyOTUxNzY4OTQwNTQ0.C_T65w.UtoElfEMr1c2pQEdsjKk4iG8QXs');
