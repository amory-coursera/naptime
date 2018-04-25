package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import conversions.CourseConversions
import conversions.PagingConversions
import org.coursera.example.Course
import org.coursera.naptime.Errors
import org.coursera.naptime.Fields
import org.coursera.naptime.GetReverseRelation
import org.coursera.naptime.MultiGetReverseRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.protobuf.CourseService.CourseServiceGrpc.CourseServiceStub
import org.coursera.protobuf.CourseService
import org.coursera.protobuf.ids.CourseId
import org.coursera.protobuf.ids.InstructorId

import scala.concurrent.ExecutionContext

@Singleton
class CoursesResource @Inject() (
    courseClient: CourseServiceStub)(implicit ec: ExecutionContext, mat: Materializer)
  extends CourierCollectionResource[String, Course] {

  override def resourceName = "courses"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Course] = BaseFields
    .withReverseRelations(
      "instructors" -> MultiGetReverseRelation(
        resourceName = ResourceName("instructors", 1),
        ids = "$instructorIds"),
      "partner" -> GetReverseRelation(
        resourceName = ResourceName("partners", 1),
        id = "$partnerId",
        description = "Partner who produces this course."),
      "courseMetadata/org.coursera.example.CertificateCourseMetadata/certificateInstructors" ->
        MultiGetReverseRelation(
          resourceName = ResourceName("instructors", 1),
          ids = "${courseMetadata/certificate/certificateInstructorIds}",
          description = "Instructor whose name and signature appears on the course certificate."),
      "courseMetadata/org.coursera.example.DegreeCourseMetadata/degreeInstructors" ->
        MultiGetReverseRelation(
          resourceName = ResourceName("instructors", 1),
          ids = "${courseMetadata/degree/degreeInstructorIds}",
          description = "Instructor whose name and signature appears on the degree certificate."))

  def get(id: String = "v1-123") = Nap.get.async { context =>
    find(courseIds = Some(Set(id)), paging = context.paging).map {
      case (response, _) => response.headOption.getOrElse(throw Errors.NotFound())
    }.map(Ok(_))
  }

  def multiGet(ids: Set[String], types: Set[String] = Set("course", "specialization")) = Nap
    .multiGet.async { context =>
    find(courseIds = Some(ids), paging = context.paging).map {
      case (response, pagination) => Ok(response).withPagination(pagination)
    }
  }

  def getAll() = Nap.getAll.async { context =>
    find(paging = context.paging).map {
      case (response, pagination) => Ok(response).withPagination(pagination)
    }
  }

  def byInstructor(instructorId: Int) = Nap.finder.async { context =>
    find(instructorIds = Some(Set(instructorId)), paging = context.paging).map {
      case (response, pagination) => Ok(response).withPagination(pagination)
    }
  }

  private[this] def find(
      courseIds: Option[Set[String]] = None,
      instructorIds: Option[Set[Int]] = None,
      paging: RequestPagination) = {

    val request = CourseService.FindCoursesRequest(
      courseIds = courseIds.map(_.toList.map(CourseId(_))).getOrElse(List.empty),
      instructorIds = instructorIds.map(_.toList.map(InstructorId(_))).getOrElse(List.empty),
      paging = PagingConversions.naptimeToProto(paging))

    courseClient
      .findCourses(request)
      .map {
        case CourseService.KeyedCourses(courses, paging) =>
          courses.map(CourseConversions.protoToCourier).map(Keyed.tupled) ->
            PagingConversions.protoToNaptime(paging)
      }
  }

}
