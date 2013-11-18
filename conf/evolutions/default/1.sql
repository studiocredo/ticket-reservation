# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table "auth_tokens" ("id" VARCHAR(254) NOT NULL PRIMARY KEY,"user_id" BIGINT NOT NULL,"creation" TIMESTAMP NOT NULL,"last_used" TIMESTAMP NOT NULL,"expiration" TIMESTAMP NOT NULL);
create table "course" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "dvd" ("id" SERIAL NOT NULL PRIMARY KEY,"event_id" BIGINT NOT NULL,"name" VARCHAR(254) NOT NULL,"price" INTEGER NOT NULL,"available-start" TIMESTAMP NOT NULL,"available-end" TIMESTAMP,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "auth_tokens_email" ("id" VARCHAR(254) NOT NULL PRIMARY KEY,"email" VARCHAR(254) NOT NULL,"user_id" BIGINT,"creation" TIMESTAMP NOT NULL,"last_used" TIMESTAMP NOT NULL,"expiration" TIMESTAMP NOT NULL);
create table "event-participants" ("event_id" BIGINT NOT NULL,"member_id" BIGINT NOT NULL,"allowed-ticket-reservations" INTEGER NOT NULL);
alter table "event-participants" add constraint "event-participants_pkey" primary key("event_id","member_id");
create table "event" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"description" VARCHAR(254) NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "group-members" ("group_id" BIGINT NOT NULL,"member_id" BIGINT NOT NULL);
alter table "group-members" add constraint "group-members-pkey" primary key("group_id","member_id");
create table "group" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"year" INTEGER NOT NULL,"course_id" BIGINT NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "member" ("id" SERIAL NOT NULL PRIMARY KEY,"user_id" BIGINT NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "order" ("id" SERIAL NOT NULL PRIMARY KEY,"user_id" BIGINT NOT NULL,"date" TIMESTAMP NOT NULL,"billing-name" VARCHAR(254) NOT NULL,"billing-address" VARCHAR(254) NOT NULL);
create table "show" ("id" SERIAL NOT NULL PRIMARY KEY,"event_id" BIGINT NOT NULL,"venue_id" BIGINT NOT NULL,"date" TIMESTAMP NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "order-ticket" ("id" SERIAL NOT NULL PRIMARY KEY,"order_id" BIGINT NOT NULL,"show_id" BIGINT NOT NULL);
create table "ticket-reservation" ("show_id" BIGINT NOT NULL,"member_id" BIGINT NOT NULL,"amount" INTEGER NOT NULL);
alter table "ticket-reservation" add constraint "ticket-reservation_pkey" primary key("show_id","member_id");
create table "order-ticket-seat" ("ticket_order_id" BIGINT NOT NULL,"show_id" BIGINT NOT NULL,"member_id" BIGINT);
alter table "order-ticket-seat" add constraint "order-ticket-seat_pkey" primary key("ticket_order_id","show_id");
create table "user_detail" ("id" BIGINT NOT NULL PRIMARY KEY,"email" VARCHAR(254),"address" VARCHAR(254),"phone" VARCHAR(254));
create table "roles" ("id" BIGINT NOT NULL,"role" VARCHAR(254) NOT NULL);
create unique index "idx_userrole" on "roles" ("id","role");
create table "user" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"username" VARCHAR(254) NOT NULL,"password" VARCHAR(254) NOT NULL,"salt" VARCHAR(254) NOT NULL);
create unique index "idx_username" on "user" ("username");
create table "venue" ("id" SERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"description" VARCHAR(254) NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
alter table "dvd" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;
alter table "event-participants" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;
alter table "event-participants" add constraint "member_fk" foreign key("member_id") references "member"("id") on update NO ACTION on delete NO ACTION;
alter table "group-members" add constraint "group_fk" foreign key("group_id") references "group"("id") on update NO ACTION on delete NO ACTION;
alter table "group-members" add constraint "member_fk" foreign key("member_id") references "member"("id") on update NO ACTION on delete NO ACTION;
alter table "group" add constraint "course_fk" foreign key("course_id") references "course"("id") on update NO ACTION on delete NO ACTION;
alter table "member" add constraint "user_fk" foreign key("user_id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "order" add constraint "user_fk" foreign key("user_id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "show" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;
alter table "show" add constraint "venue_fk" foreign key("venue_id") references "venue"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket" add constraint "order_fk" foreign key("order_id") references "order"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket" add constraint "show_fk" foreign key("show_id") references "show"("id") on update NO ACTION on delete NO ACTION;
alter table "ticket-reservation" add constraint "show_fk" foreign key("show_id") references "show"("id") on update NO ACTION on delete NO ACTION;
alter table "ticket-reservation" add constraint "member_fk" foreign key("member_id") references "member"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket-seat" add constraint "show_fk" foreign key("show_id") references "show"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket-seat" add constraint "member_fk" foreign key("member_id") references "member"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket-seat" add constraint "ticket_order_fk" foreign key("ticket_order_id") references "order-ticket"("id") on update NO ACTION on delete NO ACTION;
alter table "user_detail" add constraint "user_fk" foreign key("id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "roles" add constraint "user_fk" foreign key("id") references "user"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "dvd" drop constraint "event_fk";
alter table "event-participants" drop constraint "event_fk";
alter table "event-participants" drop constraint "member_fk";
alter table "group-members" drop constraint "group_fk";
alter table "group-members" drop constraint "member_fk";
alter table "group" drop constraint "course_fk";
alter table "member" drop constraint "user_fk";
alter table "order" drop constraint "user_fk";
alter table "show" drop constraint "event_fk";
alter table "show" drop constraint "venue_fk";
alter table "order-ticket" drop constraint "order_fk";
alter table "order-ticket" drop constraint "show_fk";
alter table "ticket-reservation" drop constraint "show_fk";
alter table "ticket-reservation" drop constraint "member_fk";
alter table "order-ticket-seat" drop constraint "show_fk";
alter table "order-ticket-seat" drop constraint "member_fk";
alter table "order-ticket-seat" drop constraint "ticket_order_fk";
alter table "user_detail" drop constraint "user_fk";
alter table "roles" drop constraint "user_fk";
drop table "auth_tokens";
drop table "course";
drop table "dvd";
drop table "auth_tokens_email";
alter table "event-participants" drop constraint "event-participants_pkey";
drop table "event-participants";
drop table "event";
alter table "group-members" drop constraint "group-members-pkey";
drop table "group-members";
drop table "group";
drop table "member";
drop table "order";
drop table "show";
drop table "order-ticket";
alter table "ticket-reservation" drop constraint "ticket-reservation_pkey";
drop table "ticket-reservation";
alter table "order-ticket-seat" drop constraint "order-ticket-seat_pkey";
drop table "order-ticket-seat";
drop table "user_detail";
drop table "roles";
drop table "user";
drop table "venue";

