import { Table, Column, Model, PrimaryKey, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'settings',
})
export class Settings extends Model<Settings> {
    @PrimaryKey
    @Column(DataType.TEXT)
    public guildid: string;

    @PrimaryKey
    @Column(DataType.TEXT)
    public key: string;

    @Column(DataType.TEXT)
    public value: string;
}
