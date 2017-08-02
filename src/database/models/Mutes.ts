import { Table, Column, Model, PrimaryKey, AutoIncrement, NotNull, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'mutelist',
})
export class Mutes extends Model<Mutes> {
    @PrimaryKey
    @AutoIncrement
    @Column(DataType.INTEGER)
    public id: number;

    @Column(DataType.TEXT)
    public muteduserid: string;

    @Column(DataType.TEXT)
    public moderatoruserid: string;

    @Column(DataType.TEXT)
    public guildid: string;

    @Column(DataType.BIGINT)
    public mutetime: number;

    @Column(DataType.BIGINT)
    public expiretime: number;

    @Column(DataType.TEXT)
    public reason: string;

    @Column(DataType.BOOLEAN)
    public expires: boolean;

    @Column(DataType.BOOLEAN)
    public unmuted: boolean;
}
