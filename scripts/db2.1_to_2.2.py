import sqlite3
import sys

if len(sys.argv) < 2:
    print('USAGE: {} <filename>'.format(sys.argv[0]))
    sys.exit(-1)

database = sqlite3.connect(sys.argv[1])
database.row_factory = sqlite3.Row
cursor = database.cursor()

cursor.execute('CREATE TABLE IF NOT EXISTS Settings (GuildID TEXT NOT NULL, Key TEXT NOT NULL, Value TEXT);')
cursor.execute('SELECT * FROM GuildSettings;')
results = cursor.fetchall()

for row in results:
    guildID = row[0]
    for pair in zip(row.keys(), row):
        if pair[0] == 'GuildID':
            continue

        key, value = pair
        values = (guildID, key, value,)
        cursor.execute('INSERT INTO Settings (GuildID, Key, Value) VALUES (?, ?, ?);', values)

database.commit()
database.close()