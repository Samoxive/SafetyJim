import { Sequelize } from 'sequelize-typescript';
import { Config } from '../config/config';
import * as path from 'path';
import { LoggerInstance } from 'winston';

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
}
