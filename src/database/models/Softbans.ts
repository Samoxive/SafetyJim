import { Table, Column, Model, PrimaryKey, AutoIncrement, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'softbanlist',
})
export class Softbans extends Model<Softbans> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public userid: string;

    @Column(DataType.TEXT)
    public moderatoruserid: string;

    @Column(DataType.TEXT)
    public guildid: string;

    @Column(DataType.BIGINT)
    public softbantime: number;

    @Column(DataType.INTEGER)
    public deletedays: number;

    @Column(DataType.TEXT)
    public reason: string;
}
