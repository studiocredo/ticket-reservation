Copy (select u.id, u.username, ud.email from "user" u join user_detail ud on u.id = ud.id order by u.id) To '/tmp/users.csv' With CSV;
