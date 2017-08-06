import { Table, Column, Model, PrimaryKey, AutoIncrement, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'kicklist',
})
export class Kicks extends Model<Kicks> {
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
    public kicktime: number;

    @Column(DataType.TEXT)
    public reason: string;
}
