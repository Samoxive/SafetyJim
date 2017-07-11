import sqlite3
import sys

if len(sys.argv) < 2:
    print('USAGE: {} <filename>'.format(sys.argv[0]))
    sys.exit(-1)

database = sqlite3.connect(sys.argv[1])
database.row_factory = sqlite3.Row
cursor = database.cursor()

cursor.execute('DELETE FROM WelcomeMessages WHERE GuildID NOT IN (SELECT p.GuildID FROM PrefixList p);')
cursor.execute('CREATE TABLE IF NOT EXISTS Settings (GuildID TEXT NOT NULL, Key TEXT NOT NULL, Value TEXT);')
cursor.execute('SELECT * FROM GuildSettings;')
results = cursor.fetchall()

print('GuildSettings row count: {}'.format(len(results)))
for row in results:
    guildID = row[0]
    for pair in zip(row.keys(), row):
        if pair[0] == 'GuildID':
            continue

        if (pair[0] == 'ModLogActive') or (pair[0] == 'HoldingRoomActive'):
            pair = (pair[0], 'true' if pair[1] == '1' else 'false')
        
        if pair[0] == 'HoldingRoomMinutes':
            pair = (pair[0], str(pair[1]))

        key, value = pair
        values = (guildID, key, value,)
        cursor.execute('INSERT INTO Settings (GuildID, Key, Value) VALUES (?, ?, ?);', values)

cursor.execute('SELECT * FROM PrefixList;')
results = cursor.fetchall()

print('PrefixList row count: {}'.format(len(results)))
for row in results:
    guildID = row[0]
    prefix = row[1]

    values = (guildID, 'Prefix', prefix,)
    cursor.execute('INSERT INTO Settings (GuildID, Key, Value) VALUES (?, ?, ?);', values)

cursor.execute('SELECT * FROM WelcomeMessages;')
results = cursor.fetchall()

print('WelcomeMessages row count: {}'.format(len(results)))
for row in results:
    guildID = row[0]
    message = row[1]

    values = (guildID, 'WelcomeMessage', message,)
    cursor.execute('INSERT INTO Settings (GuildID, Key, Value) VALUES (?, ?, ?);', values)

database.commit()
database.close()