# --- !Ups

alter table "event" add column "quota" TEXT;
update "event" set quota = '{"default":10,"values":[{"u":1,"v":5},{"u":2,"v":8},{"u":3,"v":10}]}'  where name like 'Slotshow %';


# --- !Downs

alter table "event" drop column "quota";