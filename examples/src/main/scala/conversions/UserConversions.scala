package conversions

import org.coursera.example.User
import org.coursera.protobuf.UserService
import org.coursera.protobuf.ids.UserId

object UserConversions {

  def courierToProto(id: Int, user: User): UserService.KeyedUser =
    UserService.KeyedUser(UserId(id), courierToProto(user))

  def courierToProto(user: User): UserService.User =
    UserService.User(name = user.name,
                     email = user.email,
                     selfDescription = user.selfDescription)

  def protoToCourier(keyed: UserService.KeyedUser): (Int, User) =
    keyed.id.userId -> protoToCourier(keyed.user)

  def protoToCourier(user: UserService.User): User =
    User(name = user.name,
         email = user.email,
         selfDescription = user.selfDescription)
}
