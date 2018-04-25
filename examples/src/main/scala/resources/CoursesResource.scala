package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import conversions.CourseConversions
import conversions.PagingConversions
import org.coursera.example.Course
import org.coursera.naptime.Fields
import org.coursera.naptime.GetReverseRelation
import org.coursera.naptime.MultiGetReverseRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.protobuf.CourseService.CourseServiceGrpc.CourseServiceStub
import org.coursera.protobuf.CourseService
import org.coursera.protobuf.CourseService.FindCoursesRequest
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
    courseClient
      .findCourses(FindCoursesRequest(
        courseIds = List(CourseId(id)),
        paging = PagingConversions.naptimeToProto(context.paging)))
      .map(_.courses.headOption.map(_.course).map(CourseConversions.protoToCourier))
      .map(OkIfPresent(id, _))
  }

  def multiGet(ids: Set[String], types: Set[String] = Set("course", "specialization")) = Nap
    .multiGet.async { context =>
    find(CourseService.FindCoursesRequest(
      courseIds = ids.toList.map(CourseId(_)),
      paging = PagingConversions.naptimeToProto(context.paging)))
  }

  def getAll() = Nap.getAll.async { context =>
    find(CourseService.FindCoursesRequest(
      paging = PagingConversions.naptimeToProto(context.paging)))
  }

  def byInstructor(instructorId: Int) = Nap.finder.async { context =>
    find(CourseService.FindCoursesRequest(
      instructorIds = List(InstructorId(instructorId)),
      paging = PagingConversions.naptimeToProto(context.paging)))
  }

  private[this] def find(request: CourseService.FindCoursesRequest) = {
    courseClient
      .findCourses(request)
      .map {
        case CourseService.KeyedCourses(courses, paging) =>
          Ok(courses.map(CourseConversions.protoToCourier).map(Keyed.tupled))
            .withPagination(PagingConversions.protoToNaptime(paging))
      }
  }

}
