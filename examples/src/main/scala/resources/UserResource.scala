package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import conversions.UserConversions
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.Ok
import org.coursera.example.User
import org.coursera.naptime.Errors
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.protobuf.ids.UserId
import org.coursera.protobuf.UserService
import org.coursera.protobuf.UserService.UserServiceGrpc.UserServiceStub
import play.api.libs.json.OFormat

import scala.concurrent.ExecutionContext

/**
 * This is a sample resource, illustrating the power of Naptime.
 *
 * Using `curl`, you can create and retrieve users:
 * {{{
 *   saeta@betacspro ~% curl -v -X 'POST' -H "Content-Type: application/json" -d '{"name":"a", "email":"a@b.com"}' localhost:9000/api/users.v1/
 *   Hostname was NOT found in DNS cache
 *     Trying ::1...
 *   Connected to localhost (::1) port 9000 (#0)
 *   > POST /api/users.v1/ HTTP/1.1
 *   > User-Agent: curl/7.37.1
 *   > Host: localhost:9000
 *   > Content-Type: application/json
 *   > Content-Length: 31
 *   >
 *   * upload completely sent off: 31 out of 31 bytes
 *   < HTTP/1.1 201 Created
 *   < ETag: "-347601404"
 *   < Location: /api/users.v1/1
 *   < Content-Type: application/json; charset=utf-8
 *   < X-Coursera-Id: 1
 *   < Date: Tue, 10 May 2016 22:27:23 GMT
 *   < Content-Length: 80
 *   <
 *   * Connection #0 to host localhost left intact
 *   {"elements":[{"id":1,"email":"a@b.com","name":"a"}],"paging":null,"linked":null}
 *   saeta@betacspro ~% curl localhost:9000/api/users.v1/1
 *   {"elements":[{"id":1,"email":"a@b.com","name":"a"}],"paging":null,"linked":null}
 *   saeta@betacspro ~% curl localhost:9000/api/users.v1/2
 *   {"errorCode":"notFound","message":"not found","details":null}%
 * }}}
 */
@Singleton
class UsersResource @Inject() (
    userClient: UserServiceStub)
    (implicit override val executionContext: ExecutionContext,
    override val materializer: Materializer)
  extends TopLevelCollectionResource[Int, User] {

  override def resourceName = "users"
  override def resourceVersion = 1  // optional; defaults to 1
  implicit val fields = Fields.withDefaultFields(  // default field projection
    "id", "name", "email")

  override def keyFormat: KeyFormat[KeyType] = KeyFormat.intKeyFormat
  override implicit def resourceFormat: OFormat[User] = CourierFormats.recordTemplateFormats[User]

  def get(id: Int) = Nap.get.async { context =>
    find(userIds = Some(Set(id)))
      .map(_.headOption.getOrElse(throw Errors.NotFound()))
      .map(Ok(_))
  }

  def multiGet(ids: Set[Int]) = Nap.multiGet.async { context =>
    find(userIds = Some(ids)).map(Ok(_))
  }

  def getAll() = Nap.getAll.async { context =>
    find().map(Ok(_))
  }

  def create() = Nap
    .jsonBody[User]
    .create
    .async { context =>
      val user = context.body
      userClient
        .createUser(UserConversions.courierToProto(user))
        .map(id => Ok(Keyed(id.userId, Some(user))))
    }

  def email(email: String) = Nap.finder.async { context =>
    find(userEmails = Some(Set(email))).map(Ok(_))
  }

  private[this] def find(
      userIds: Option[Set[Int]] = None,
      userEmails: Option[Set[String]] = None) = {
    val request = UserService.FindUsersRequest(
      userIds = userIds.map(_.map(UserId(_)).toList).getOrElse(List.empty),
      emails = userEmails.map(_.toList).getOrElse(List.empty))
    userClient
      .findUsers(request)
      .map(_.users.map(UserConversions.protoToCourier).map(Keyed.tupled))
  }

}

class UserBanManager {
  @volatile
  var bannedUsers = Set.empty[Int]
}
