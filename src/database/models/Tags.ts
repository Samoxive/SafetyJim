import { Table, Column, Model, PrimaryKey, DataType } from 'sequelize-typescript';

@Table({
    tableName: 'taglist',
})
export class Tags extends Model<Tags> {
    @PrimaryKey
    @Column(DataType.TEXT)
    public guildid: string;

    @PrimaryKey
    @Column(DataType.TEXT)
    public name: string;

    @Column(DataType.TEXT)
    public response: string;
}
