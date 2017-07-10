import sqlite3
import sys

if len(sys.argv) < 2:
    print('USAGE: {} <filename>'.format(sys.argv[0]))
    sys.exit(-1)

database = sqlite3.connect(sys.argv[1])
cursor = database.cursor()

cursor.execute('SELECT * FROM GuildSettings;')
results = cursor.fetchall()
print(results)