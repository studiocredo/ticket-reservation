event=$1

cat <<EOF
with
	reservations as (select o.user_id, o.show_id, count(1) as quantity from "order-ticket-seat" o join "show" s on o.show_id = s.id join "event" e on s.event_id = e.id where e.name = '$event' and o.user_id is not null group by o.user_id, o.show_id),
	prereservations as (select p.user_id, p.show_id, p.quantity from "show-prereservations" p join "show" s on p.show_id = s.id join "event" e on s.event_id = e.id where e.name = '$event')
select u.id,  u.name, ud.email, p.show_id, p.quantity as preres, coalesce(r.quantity,0) as res from "prereservations" p left join "reservations" r on p.user_id = r.user_id and p.show_id = r.show_id left join "user" u on p.user_id = u.id left join "user_detail" ud on u.id = ud.id where p.quantity > r.quantity or r.quantity is null
EOF