package conversions

import org.coursera.example.User

object CourierToProto {

  def user(id: Int, user: User) = org.coursera.example.protos.user.User(
    id = id,
    name = user.name,
    email = user.email)

}