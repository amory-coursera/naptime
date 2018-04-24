package conversions

import org.coursera.example.User
import org.coursera.protobuf.UserService

object UserConversions {

  def courierToProto(id: Int, user: User): UserService.KeyedUser =
    UserService.KeyedUser(UserService.UserId(id), courierToProto(user))

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
