import { Table, Column, Model, PrimaryKey, AutoIncrement, NotNull, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'commandlogs',
})
export class CommandLogs extends Model<CommandLogs> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public command: string;

    @Column(DataType.TEXT)
    public arguments: string;

    @Column(DataType.TEXT)
    public time: string;

    @Column(DataType.BIGINT)
    public timestamp: number;

    @Column(DataType.TEXT)
    public username: string;

    @Column(DataType.TEXT)
    public userid: string;

    @Column(DataType.TEXT)
    public guildname: string;

    @Column(DataType.TEXT)
    public guildid: string;
}
