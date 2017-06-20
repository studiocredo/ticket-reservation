event=$1

cat <<EOF
Copy(with
	orders as (select ot.order_id, sum(ost.price) as price from "order-ticket-seat" ost join "order-ticket" ot on ost.ticket_order_id = ot.id join "show" s on ot.show_id = s.id join "event" e on s.event_id = e.id where e.name = '$event' group by ot.order_id)
select u.id, u.name, ud.email, o.order_id, o.price from "orders" o left join "order" oo on o.order_id = oo.id left join "user" u on oo.user_id = u.id left join "user_detail" ud on u.id = ud.id order by u.id, oo.id) To STDOUT With CSV DELIMITER ',';

EOF
