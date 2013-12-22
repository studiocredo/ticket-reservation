# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table "auth_tokens" ("id" VARCHAR(254) NOT NULL PRIMARY KEY,"user_id" BIGINT NOT NULL,"creation" TIMESTAMP NOT NULL,"last_used" TIMESTAMP NOT NULL,"expiration" TIMESTAMP NOT NULL);
create table "dvd" ("id" SERIAL NOT NULL PRIMARY KEY,"event_id" BIGINT NOT NULL,"name" TEXT NOT NULL,"price" INTEGER NOT NULL,"available-start" TIMESTAMP NOT NULL,"available-end" TIMESTAMP,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "auth_tokens_email" ("id" VARCHAR(254) NOT NULL PRIMARY KEY,"email" TEXT NOT NULL,"user_id" BIGINT,"creation" TIMESTAMP NOT NULL,"last_used" TIMESTAMP NOT NULL,"expiration" TIMESTAMP NOT NULL);
create table "event" ("id" SERIAL NOT NULL PRIMARY KEY,"name" TEXT NOT NULL,"description" TEXT NOT NULL,"preReservationStart" TIMESTAMP,"preReservationEnd" TIMESTAMP,"reservationStart" TIMESTAMP,"reservationEnd" TIMESTAMP,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "order" ("id" SERIAL NOT NULL PRIMARY KEY,"user_id" BIGINT NOT NULL,"date" TIMESTAMP NOT NULL,"billing-name" TEXT NOT NULL,"billing-address" TEXT NOT NULL);
create table "reservation-quota" ("event_id" BIGINT NOT NULL,"user_id" BIGINT NOT NULL,"quota" INTEGER NOT NULL);
alter table "reservation-quota" add constraint "event-user_pkey" primary key("event_id","user_id");
create table "show-prereservations" ("show_id" BIGINT NOT NULL,"user_id" BIGINT NOT NULL,"quantity" INTEGER NOT NULL);
alter table "show-prereservations" add constraint "show-user_pkey" primary key("show_id","user_id");
create table "show" ("id" SERIAL NOT NULL PRIMARY KEY,"event_id" BIGINT NOT NULL,"venue_id" BIGINT NOT NULL,"date" TIMESTAMP NOT NULL,"archived" BOOLEAN DEFAULT false NOT NULL);
create table "order-ticket" ("id" SERIAL NOT NULL PRIMARY KEY,"order_id" BIGINT NOT NULL,"show_id" BIGINT NOT NULL);
create table "order-ticket-seat" ("ticket_order_id" BIGINT NOT NULL,"show_id" BIGINT NOT NULL,"user_id" BIGINT,"row" INTEGER NOT NULL,"seat" INTEGER NOT NULL,"price" DECIMAL(21,2) NOT NULL);
alter table "order-ticket-seat" add constraint "order-ticket-seat_pkey" primary key("ticket_order_id","show_id");
create unique index "idx_showseat" on "order-ticket-seat" ("show_id","row","seat");
create table "user_detail" ("id" BIGINT NOT NULL PRIMARY KEY,"email" TEXT,"address" TEXT,"phone" TEXT);
create table "roles" ("id" BIGINT NOT NULL,"role" TEXT NOT NULL);
create unique index "idx_userrole" on "roles" ("id","role");
create table "user" ("id" SERIAL NOT NULL PRIMARY KEY,"name" TEXT NOT NULL,"username" TEXT NOT NULL,"password" TEXT NOT NULL,"salt" TEXT NOT NULL);
create unique index "idx_username" on "user" ("username");
create table "venue" ("id" SERIAL NOT NULL PRIMARY KEY,"name" TEXT NOT NULL,"description" TEXT NOT NULL,"floorplan" TEXT,"archived" BOOLEAN DEFAULT false NOT NULL);
alter table "dvd" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;
alter table "order" add constraint "user_fk" foreign key("user_id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "reservation-quota" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;
alter table "reservation-quota" add constraint "user_fk" foreign key("user_id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "show-prereservations" add constraint "show_fk" foreign key("show_id") references "show"("id") on update NO ACTION on delete NO ACTION;
alter table "show-prereservations" add constraint "user_fk" foreign key("user_id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "show" add constraint "venue_fk" foreign key("venue_id") references "venue"("id") on update NO ACTION on delete NO ACTION;
alter table "show" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket" add constraint "show_fk" foreign key("show_id") references "show"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket" add constraint "order_fk" foreign key("order_id") references "order"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket-seat" add constraint "ticket_order_fk" foreign key("ticket_order_id") references "order-ticket"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket-seat" add constraint "show_fk" foreign key("show_id") references "show"("id") on update NO ACTION on delete NO ACTION;
alter table "order-ticket-seat" add constraint "user_fk" foreign key("user_id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "user_detail" add constraint "user_fk" foreign key("id") references "user"("id") on update NO ACTION on delete NO ACTION;
alter table "roles" add constraint "user_fk" foreign key("id") references "user"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "dvd" drop constraint "event_fk";
alter table "order" drop constraint "user_fk";
alter table "reservation-quota" drop constraint "event_fk";
alter table "reservation-quota" drop constraint "user_fk";
alter table "show-prereservations" drop constraint "show_fk";
alter table "show-prereservations" drop constraint "user_fk";
alter table "show" drop constraint "venue_fk";
alter table "show" drop constraint "event_fk";
alter table "order-ticket" drop constraint "show_fk";
alter table "order-ticket" drop constraint "order_fk";
alter table "order-ticket-seat" drop constraint "ticket_order_fk";
alter table "order-ticket-seat" drop constraint "show_fk";
alter table "order-ticket-seat" drop constraint "user_fk";
alter table "user_detail" drop constraint "user_fk";
alter table "roles" drop constraint "user_fk";
drop table "auth_tokens";
drop table "dvd";
drop table "auth_tokens_email";
drop table "event";
drop table "order";
alter table "reservation-quota" drop constraint "event-user_pkey";
drop table "reservation-quota";
alter table "show-prereservations" drop constraint "show-user_pkey";
drop table "show-prereservations";
drop table "show";
drop table "order-ticket";
alter table "order-ticket-seat" drop constraint "order-ticket-seat_pkey";
drop table "order-ticket-seat";
drop table "user_detail";
drop table "roles";
drop table "user";
drop table "venue";

