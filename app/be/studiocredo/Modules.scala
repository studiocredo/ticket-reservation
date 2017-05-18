package be.studiocredo

import com.google.inject.{AbstractModule, Singleton}
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import be.studiocredo.auth.AuthTokenExpireService
import be.studiocredo.aws.DownloadService
import be.studiocredo.reservations.ReservationEngineMonitorService

object Modules {

  class BaseModule extends AbstractModule with ScalaModule {
    def configure() {
      binder().disableCircularProxies()

      import be.studiocredo._
      bind[EventService].in[Singleton]
      bind[UserService].in[Singleton]
      bind[VenueService].in[Singleton]
      bind[ShowService].in[Singleton]
      bind[OrderService].in[Singleton]
      bind[PreReservationService].in[Singleton]
      bind[NotificationService].in[Singleton]
      bind[PaymentService].in[Singleton]
      bind[TicketService].in[Singleton]
      bind[ReservationEngineMonitorService].asEagerSingleton()
      bind[DownloadService].asEagerSingleton()

      bind[controllers.Application].in[Singleton]
      bind[controllers.admin.EventDetails].in[Singleton]
      bind[controllers.admin.Events].in[Singleton]
      bind[controllers.admin.UserDetails].in[Singleton]
      bind[controllers.admin.Venues].in[Singleton]
      bind[controllers.admin.Orders].in[Singleton]
      bind[controllers.Orders].in[Singleton]
      bind[controllers.Events].in[Singleton]
    }
  }

  class AuthModule extends AbstractModule with ScalaModule {
    def configure() {
      import be.studiocredo.auth._
      binder().disableCircularProxies()

      bind[AuthenticatorService].in[Singleton]
//      bind[AuthTokenStore].to[CacheAuthTokenStore].in[Singleton]
      bind[AuthTokenStore].to[DbAuthTokenStore].in[Singleton]
      bind[IdentityService].in[Singleton]

      bind[AuthTokenExpireService].asEagerSingleton()
      // todo replace with method that doesn't suck
      val multi = ScalaMultibinder.newSetBinder[Service](binder)
      multi.addBinding().to[AuthTokenExpireService]
      multi.addBinding().to[ReservationEngineMonitorService]
      multi.addBinding().to[DownloadService]

      bind[controllers.auth.Auth].in[Singleton]
      bind[controllers.auth.LoginPage].in[Singleton]
      bind[controllers.auth.PasswordChange].in[Singleton]
      bind[controllers.auth.SignUp].in[Singleton]
      bind[controllers.auth.PasswordReset].in[Singleton]
    }
  }
}
