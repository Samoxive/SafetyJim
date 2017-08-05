import { Table, Column, Model, PrimaryKey, DataType, AutoIncrement } from 'sequelize-typescript';

@Table({
    tableName: 'joinlist',
})
export class Joins extends Model<Joins> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public userid: string;

    @Column(DataType.TEXT)
    public guildid: string;

    @Column(DataType.BIGINT)
    public jointime: number;

    @Column(DataType.BIGINT)
    public allowtime: number;

    @Column(DataType.BOOLEAN)
    public allowed: boolean;
}
