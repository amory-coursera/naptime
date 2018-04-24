package resources

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.Ok
import org.coursera.example.User
import org.coursera.naptime.Errors
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.example.coursera.proto.User.Auth
import org.example.coursera.proto.User.CreateUserRequest
import org.example.coursera.proto.User.FindUsersRequest
import org.example.coursera.proto.User.FindUsersResponse
import org.example.coursera.proto.User.UserData
import org.example.coursera.proto.User.UserData.SelfDescriptionPresent
import play.api.libs.json.OFormat
import org.example.coursera.proto.User.UserRecord
import org.example.coursera.proto.User.UserServiceGrpc

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
class UsersResource @Inject()(userStore: UserStore,
                              userReader: UserServiceGrpc.UserServiceStub)(
    implicit override val executionContext: ExecutionContext,
    override val materializer: Materializer)
    extends TopLevelCollectionResource[Int, User] {

  override def resourceName = "users"
  override def resourceVersion = 1 // optional; defaults to 1
  implicit val fields = Fields.withDefaultFields( // default field projection
                                                 "id",
                                                 "name",
                                                 "email")

  override def keyFormat: KeyFormat[KeyType] = KeyFormat.intKeyFormat
  override implicit def resourceFormat: OFormat[User] =
    CourierFormats.recordTemplateFormats[User]

  def get(id: Int) = Nap.get.async { context =>
    userReader
      .findUsers(
        FindUsersRequest(Some(Auth(isSuperuser = true)), userIds = List(id)))
      .map(
        _.users.headOption
          .map(UserProtoConversions.makeCourier)
          .map(Keyed.tupled))
      .map(_.map(Ok(_)).getOrElse(throw Errors.NotFound()))
  }

  def multiGet(ids: Set[Int]) = Nap.multiGet.async { context =>
    userReader
      .findUsers(
        FindUsersRequest(Some(Auth(isSuperuser = true)), userIds = ids.toList))
      .map(_.users.map(UserProtoConversions.makeCourier).map(Keyed.tupled))
      .map(Ok(_))
  }

  def getAll() = Nap.getAll { context =>
    Ok(userStore.all().map { case (id, user) => Keyed(id, user) }.toList)
  }

  def create() =
    Nap
      .jsonBody[User]
      .create
      .async { context =>
        userReader
          .createUser(
            CreateUserRequest(
              Some(Auth(isSuperuser = true)),
              Some(UserProtoConversions.makeProto(context.body))))
          .map(UserProtoConversions.makeCourier)
          .map { case (id, value) => Ok(Keyed(id, Some(value))) }
      }

  def email(email: String) = Nap.finder.async { context =>
    userReader
      .findUsers(
        FindUsersRequest(Some(Auth(isSuperuser = true)), emails = List(email)))
      .map(_.users.map(UserProtoConversions.makeCourier).map(Keyed.tupled))
      .map(Ok(_))
  }

}

trait UserStore {
  def get(id: Int): Option[User]
  def create(user: User): Int
  def all(): Map[Int, User]
}

@Singleton
class UserStoreImpl extends UserStore {
  @volatile
  var userStore = Map.empty[Int, User]
  val nextId = new AtomicInteger(0)

  def get(id: Int) = userStore.get(id)

  def create(user: User): Int = {
    val id = nextId.incrementAndGet()
    userStore = userStore + (id -> user)
    id
  }

  def all() = userStore

}

class UserBanManager {
  @volatile
  var bannedUsers = Set.empty[Int]
}

object UserProtoConversions {

  def makeProto(user: User): UserData = {
    UserData(
      name = user.name,
      email = user.email,
      selfDescriptionPresent = user.selfDescription
        .map(SelfDescriptionPresent.SelfDescription)
        .getOrElse(SelfDescriptionPresent.Empty)
    )
  }

  def makeProto(id: Int, user: User): UserRecord = {
    UserRecord(id, Some(makeProto(user)))
  }

  def makeCourier(user: UserData): User = {
    User(name = user.name,
         email = user.email,
         selfDescription = user.selfDescriptionPresent.selfDescription)
  }

  def makeCourier(user: UserRecord): (Int, User) = {
    user.id -> makeCourier(user.user.get)
  }
}

class UserReader @Inject()(userStore: UserStore)
    extends UserServiceGrpc.UserService {

  override def findUsers(request: FindUsersRequest) = Future.successful {
    val users = if (request.userIds.nonEmpty) {
      userStore.all().filterKeys(request.userIds.contains)
    } else if (request.emails.nonEmpty) {
      userStore.all().filter {
        case (_, user) =>
          request.emails.contains(user.email)
      }
    } else {
      Map.empty
    }

    val authorizedUsers = if (request.auth.exists(_.isSuperuser)) {
      users
    } else {
      users.filterKeys(userId => request.auth.exists(_.requester == userId))
    }
    FindUsersResponse(users.toList.map {
      case (id, user) => UserProtoConversions.makeProto(id, user)
    })
  }

  override def createUser(request: CreateUserRequest) = Future.successful {
    val isSuperuser = request.auth.exists(_.isSuperuser) // TODO: what if 'no'?
    val userData = request.user.get
    val userId = userStore.create(UserProtoConversions.makeCourier(request.user.get))
    UserRecord(userId, Some(userData))
  }
}
