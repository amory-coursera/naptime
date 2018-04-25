package stores

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

import org.coursera.example.User

@Singleton
class UserStore {
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
