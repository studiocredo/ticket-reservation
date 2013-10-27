package models

object queries {

  import play.api.db.slick.Config.driver.simple._

  import slick.lifted.BaseTypeMapper

  // aliases for shorter notation
  type C[Type] = Column[Type]
  type Q[Table, Element] = Query[Table, Element]
  type BTM[Type] = BaseTypeMapper[Type]


  /*
  implicit def extendAll[E, T](q: Q[T, E]) = new {
    def paginate(page: Int, pageSize: Int): Q[T, E] = q.drop(pageSize * page).take(pageSize)
  }
  */

  def paginate[E, T](q: Q[T, E],page: Int, pageSize: Int): Q[T, E] = q.drop(pageSize * page).take(pageSize)
}
