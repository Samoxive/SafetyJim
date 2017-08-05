import { Sequelize } from 'sequelize-typescript';
import { Config } from '../config/config';
import * as path from 'path';
import { LoggerInstance } from 'winston';
import { Guild } from 'discord.js';
import { Settings } from './models/Settings';

export const defaultWelcomeMessage = 'Welcome to $guild $user!';

export type SettingKey = 'modlogactive' | 'modlogchannelid' | 'holdingroomroleid' | 'holdingroomactive' |
'holdingroomminutes' | 'welcomemessagechannelid' | 'embedcolor' | 'prefix' | 'welcomemessage' | 'welcomemessageactive';

export class BotDatabase {
    public database: Sequelize;

    constructor(private config: Config, private log: LoggerInstance) {
        let databaseConfig = config.database;
        this.database = new Sequelize({
            name: databaseConfig.name,
            dialect: 'postgres',
            username: databaseConfig.user,
            password: databaseConfig.pass,
            host: databaseConfig.host,
            port: databaseConfig.port,
            modelPaths: [path.join(__dirname, 'models')],
            logging: false,
        });
    }

    public async init(): Promise<BotDatabase> {
        try {
            await this.database.authenticate();
        } catch (err) {
            this.log.error(`Failed to connect to postgresql database, terminating... ${err.stack}`);
        }

        await this.database.sync();

        return this;
    }

    public async getGuildSetting(guild: Guild, key: SettingKey): Promise<string> {
        return (await Settings.find<Settings>({
            where: {
                guildid: guild.id,
                key,
            },
        })).value;
    }

    public async getGuildSettings(guild: Guild): Promise<Map<SettingKey, string>> {
        let settings = await Settings.findAll<Settings>({
            where: {
                guildid: guild.id,
            },
        });

        let result = new Map<SettingKey, string>();

        for (let setting of settings) {
            result.set(setting.key as SettingKey, setting.value);
        }

        return result;
    }

    public async getValuesOfKey(key: SettingKey): Promise<Map<string, string>> {
        let result = new Map<string, string>();
        let rows = await Settings.findAll<Settings>({
            where: {
                key,
            },
        });

        for (let row of rows) {
            result.set(row.guildid, row.value);
        }

        return result;
    }

    public async createGuildSettings(guild: Guild): Promise<void> {
        await this.createKeyValueSetting(guild, 'modlogactive', 'false');
        await this.createKeyValueSetting(guild, 'modlogchannelid', guild.defaultChannel.id);
        await this.createKeyValueSetting(guild, 'holdingroomroleid', null);
        await this.createKeyValueSetting(guild, 'holdingroomactive', 'false');
        await this.createKeyValueSetting(guild, 'holdingroomminutes', '3');
        await this.createKeyValueSetting(guild, 'embedcolor', '4286F4');
        await this.createKeyValueSetting(guild, 'prefix', this.config.jim.default_prefix);
        await this.createKeyValueSetting(guild, 'welcomemessageactive', 'false');
        await this.createKeyValueSetting(guild, 'welcomemessage', defaultWelcomeMessage);
        await this.createKeyValueSetting(guild, 'welcomemessagechannelid', guild.defaultChannel.id);
    }

    private async createKeyValueSetting(guild: Guild, key: string, value: string): Promise<void> {
        await Settings.create<Settings>({ guildid: guild.id, key, value });
    }
}

export let possibleKeys = ['ModLogActive', 'ModLogChannelID', 'HoldingRoomRoleID', 'HoldingRoomActive',
    'HoldingRoomMinutes', 'WelcomeMessageChannelID', 'EmbedColor', 'Prefix', 'WelcomeMessage', 'WelcomeMessageActive'];
