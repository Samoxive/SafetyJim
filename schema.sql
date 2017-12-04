create table banlist
(
	id serial not null
		constraint banlist_pkey
			primary key,
	userid text,
	moderatoruserid text,
	guildid text,
	bantime bigint,
	expiretime bigint,
	reason text,
	expires boolean,
	unbanned boolean
)
;

create table commandlogs
(
	id serial not null
		constraint commandlogs_pkey
		primary key,
	command text,
	arguments text,
	time timestamp with time zone,
	username text,
	userid text,
	guildname text,
	guildid text
)
;

create table joinlist
(
	id serial not null
		constraint joinlist_pkey
		primary key,
	userid text,
	guildid text,
	jointime bigint,
	allowtime bigint,
	allowed boolean
)
;

create table kicklist
(
	id serial not null
		constraint kicklist_pkey
		primary key,
	userid text,
	moderatoruserid text,
	guildid text,
	kicktime bigint,
	reason text
)
;

create table mutelist
(
	id serial not null
		constraint mutelist_pkey
		primary key,
	userid text,
	moderatoruserid text,
	guildid text,
	mutetime bigint,
	expiretime bigint,
	reason text,
	expires boolean,
	unmuted boolean
)
;

create table reminderlist
(
	id serial not null
		constraint reminderlist_pkey
		primary key,
	userid text,
	channelid text,
	guildid text,
	createtime bigint,
	remindtime bigint,
	reminded boolean,
	message text
)
;

create table softbanlist
(
	id serial not null
		constraint softbanlist_pkey
		primary key,
	userid text,
	moderatoruserid text,
	guildid text,
	softbantime bigint,
	deletedays integer,
	reason text
)
;

create table taglist
(
	guildid text not null,
	name text not null,
	response text,
	constraint taglist_pkey
	primary key (guildid, name)
)
;

create table warnlist
(
	id serial not null
		constraint warnlist_pkey
		primary key,
	userid text,
	moderatoruserid text,
	guildid text,
	warntime bigint,
	reason text
)
;

CREATE TABLE rolelist
(
    guildid TEXT,
    roleid TEXT,
    constraint rolelist_pkey
    primary key (guildid, roleid)
)
;

CREATE TABLE settings
(
    guildid TEXT constraint settings_pkey primary key,
    modlog BOOLEAN,
    modlogchannelid TEXT,
    holdingroom BOOLEAN,
		holdingroomroleid TEXT,
		holdingroomminutes INTEGER,
		invitelinkremover BOOLEAN,
		welcomemessage BOOLEAN,
		message TEXT,
		welcomemessagechannelid TEXT,
		prefix TEXT,
		silentcommands BOOLEAN,
		nospaceprefix BOOLEAN,
		statistics BOOLEAN
)
;

CREATE TABLE messages
(
  messageid TEXT CONSTRAINT messages_pkey PRIMARY KEY,
  userid TEXT,
  guildid TEXT,
  channelid TEXT,
  date BIGINT,
  wordcount INTEGER,
  size INTEGER
)
;

CREATE TABLE membercounts
(
  id SERIAL NOT NULL CONSTRAINT membercounts_pkey PRIMARY KEY,
  guildid TEXT,
  date BIGINT,
  onlinecount INTEGER,
  count INTEGER
)
;