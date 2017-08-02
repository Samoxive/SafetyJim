import { Table, Column, Model, PrimaryKey, AutoIncrement, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'banlist',
})
export class Bans extends Model<Bans> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public banneduserid: string;

    @Column(DataType.TEXT)
    public moderatoruserid: string;

    @Column(DataType.TEXT)
    public guildid: string;

    @Column(DataType.BIGINT)
    public bantime: number;

    @Column(DataType.BIGINT)
    public expiretime: number;

    @Column(DataType.TEXT)
    public reason: string;

    @Column(DataType.BOOLEAN)
    public expires: boolean;

    @Column(DataType.BOOLEAN)
    public unbanned: boolean;
}
