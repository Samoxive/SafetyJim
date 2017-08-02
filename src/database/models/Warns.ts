import { Table, Column, Model, PrimaryKey, AutoIncrement, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'warnlist',
})
export class Warns extends Model<Warns> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public warneduserid: string;

    @Column(DataType.TEXT)
    public moderatoruserid: string;

    @Column(DataType.TEXT)
    public guildid: string;

    @Column(DataType.BIGINT)
    public warntime: number;

    @Column(DataType.TEXT)
    public reason: string;
}
