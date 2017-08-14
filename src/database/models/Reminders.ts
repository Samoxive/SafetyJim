import { Table, Column, Model, PrimaryKey, AutoIncrement, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'remindlist',
})
export class Reminds extends Model<Reminds> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public userid: string;

    @Column(DataType.TEXT)
    public channelid: string;

    @Column(DataType.TEXT)
    public guildid: string;

    @Column(DataType.BIGINT)
    public createtime: number;

    @Column(DataType.BIGINT)
    public remindtime: number;

    @Column(DataType.TEXT)
    public message: string;
}
