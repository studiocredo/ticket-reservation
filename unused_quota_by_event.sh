event=$1

cat <<EOF
copy(
with
	prereservations as (select p.user_id as user_id, sum(p.quantity) as quantity from "show-prereservations" p join "show" s on p.show_id = s.id join "event" e on s.event_id = e.id where e.name = '$event' group by p.user_id),
	quota as (select q.user_id as user_id, sum(q.quota) as quota from "reservation-quota" q join "event" e on q.event_id = e.id where e.name = '$event' group by q.user_id)
select x.username,u.email from "quota" q left join "prereservations" p on q.user_id = p.user_id join "user_detail" u on q.user_id = u.id left join "user" x on u.id = x.id where p.quantity is null) To STDOUT With CSV DELIMITER ',';
 
EOF
