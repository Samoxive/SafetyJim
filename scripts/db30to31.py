import csv
import sys
import pprint

args = sys.argv

if len(args) == 1:
    sys.exit(1)

output_dict = {}
path = args[1]
csv_file = []

with open(path, encoding="utf-8") as tsv:
    for line in csv.reader(tsv, dialect="excel-tab"):
        csv_file.append(line)

csv_file = csv_file[1:]

for row in csv_file:
    [guild_id, key, value] = row
    try:
        t = output_dict[guild_id]
    except KeyError:
        output_dict[guild_id] = {}

    if key == "modlogactive":
        key = "modlog"
    elif key == "holdingroomactive":
        key = "holdingroom"
    elif key == "welcomemessageactive":
        key = "welcomemessage"
    elif key == "welcomemessage":
        key = "message"
    
    output_dict[guild_id][key] = value

output_rows = []

for k, v in output_dict.items():
    output_rows.append([
        k, v["modlog"], v["modlogchannelid"], v["holdingroom"],
        v["holdingroomroleid"], v["holdingroomminutes"], v["invitelinkremover"],
        v["welcomemessage"], v["message"], v["welcomemessagechannelid"],
        v["prefix"], v["silentcommands"]
    ])

with open("output.tsv", "w", encoding="utf-8") as output_file:
    writer = csv.writer(output_file, dialect="excel-tab", lineterminator="\n")
    writer.writerows(output_rows)