package rpc

import javax.inject.Inject

import conversions.CourierToProto
import org.coursera.example.protos.auth.Requestor
import org.coursera.example.protos.user.Error
import org.coursera.example.protos.user.UserListResponse
import org.coursera.example.protos.user.UserId
import org.coursera.example.protos.user.UserReaderGrpc.UserReader
import org.coursera.example.protos.user.UsersRequest
import resources.UserStore

import scala.concurrent.Future

class UserService @Inject() (userStore: UserStore) () extends UserReader {

  override def all(request: Requestor) = Future.successful {
    if (request.superuser) {
      UserListResponse(users =
        userStore.all().toList.map(CourierToProto.user.tupled))
    } else {
      UserListResponse(
        errors = List(Error("Requestor must be a superuser"))
      )
    }
  }

  override def filtered(request: UsersRequest) = Future.successful {
    val users = request.userIds.map(userId => userStore.get(userId).map(userId -> _)) ++
      request.userEmails.flatMap(userStore.byEmail(_)).toList

  }
}
