package rpc

import javax.inject.Inject
import javax.inject.Singleton

import conversions.UserConversions
import org.coursera.protobuf.UserService
import org.coursera.protobuf.UserService.UserServiceGrpc
import resources.UserStore

import scala.concurrent.Future

@Singleton
class UserServiceImpl @Inject() (userStore: UserStore) extends UserServiceGrpc.UserService {

  override def findUsers(request: UserService.FindUsersRequest) = Future.successful {
    val users = userStore.all().filter { case (id, user) =>
      (request.userIds.isEmpty || request.userIds.contains(UserService.UserId(id))) &&
        (request.emails.isEmpty || request.emails.contains(user.email))
    }.toList.map { case (id, user) => UserConversions.courierToProto(id, user) }
    UserService.KeyedUsers(users)
  }

  override def createUser(request: UserService.User) = Future.successful {
    UserService.UserId(userStore.create(UserConversions.protoToCourier(request)))
  }

}
