---DDL for PostgreSQL DB from 1st task
create table numbers (
	sim varchar not null,
	constraint numbers_pk primary key (sim)
);

create table cdr_holder (
	id serial4 not null,
	sim varchar null,
	unix_start int4 null,
	unix_end int4 null,
	incoming varchar null,
	constraint cdr_holder_pk primary key (id)
);

insert into numbers values (7968969935), (74571938267), (71364416478),
						   (7747873230), (74982406633), (787845253770),
						   (74374224157), (75326984736), (76168793160),
						   (79298674093);
