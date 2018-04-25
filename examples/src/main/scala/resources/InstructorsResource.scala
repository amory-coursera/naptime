package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import conversions.InstructorConversions
import org.coursera.example.Instructor
import org.coursera.naptime.Errors
import org.coursera.naptime.Fields
import org.coursera.naptime.FinderReverseRelation
import org.coursera.naptime.GetReverseRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.protobuf.InstructorService
import org.coursera.protobuf.ids.InstructorId
import stores.InstructorStore

import scala.concurrent.ExecutionContext

@Singleton
class InstructorsResource @Inject() (
    instructorClient: InstructorService.InstructorServiceGrpc.InstructorServiceStub)(implicit ec: ExecutionContext,
    mat: Materializer)
  extends CourierCollectionResource[Int, Instructor] {

  override def resourceName = "instructors"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Instructor] = BaseFields
    .withReverseRelations(
      "courses" -> FinderReverseRelation(
        resourceName = ResourceName("courses", 1),
        finderName = "byInstructor",
        arguments = Map("instructorId" -> "$id")),
      "partner" -> GetReverseRelation(
        resourceName = ResourceName("partners", 1),
        id = "$partnerId"))

  def get(id: Int) = Nap.get.async { context =>
    find(instructorIds = Some(Set(id)))
      .map(_.headOption.getOrElse(throw Errors.NotFound()))
      .map(Ok(_))
  }

  def multiGet(ids: Set[Int]) = Nap.multiGet.async { context =>
    find(instructorIds = Some(ids)).map(Ok(_))
  }

  def getAll() = Nap.getAll.async { context =>
    find().map(Ok(_))
  }

  private[this] def find(
      instructorIds: Option[Set[Int]] = None) = {
    val request = InstructorService.FindInstructorsRequest(
      instructorIds = instructorIds.map(_.map(InstructorId(_)).toList).getOrElse(List.empty))
    instructorClient
      .findInstructors(request)
      .map(_.instructor.map(InstructorConversions.protoToCourier).map(Keyed.tupled))
  }

}
