package conversions

import com.google.protobuf.ByteString
import com.linkedin.data.DataMap
import org.coursera.example.AnyData
import org.coursera.example.CertificateCourseMetadata
import org.coursera.example.Course
import org.coursera.example.CourseMetadata
import org.coursera.example.DegreeCourseMetadata
import org.coursera.naptime.courier.CourierFormats
import org.coursera.protobuf.CourseService
import org.coursera.protobuf.ids._
import play.api.libs.json.Json

object CourseConversions {

  private implicit val anyDataFormat = CourierFormats.recordTemplateFormats[AnyData]

  def courierToProto(id: String, course: Course): CourseService.KeyedCourse =
    CourseService.KeyedCourse(CourseId(id), courierToProto(course))

  def courierToProto(course: Course): CourseService.Course =
    CourseService.Course(
      instructorIds = course.instructorIds.toList.map(InstructorId(_)),
      partnerId = PartnerId(course.partnerId),
      slug = course.slug,
      name = course.name,
      description = course.description,
      extraData = ByteString.copyFrom(
        Json.stringify(Json.toJson(course.extraData)).getBytes),
      courseMetadata = courierToProto(course.courseMetadata)
    )

  def courierToProto(
      courseMetadata: CourseMetadata): CourseService.Course.CourseMetadata = {
    courseMetadata match {
      case CourseMetadata.CertificateCourseMetadataMember(member) =>
        CourseService.Course.CourseMetadata.Certificate(
          CourseService.CertificateCourseMetadata(
            member.certificateInstructorIds.toList.map(InstructorId(_))))
      case CourseMetadata.DegreeCourseMetadataMember(member) =>
        CourseService.Course.CourseMetadata.Degree(
          CourseService.DegreeCourseMetadata(
            member.degreeCertificateName,
            member.degreeInstructorIds.toList.map(InstructorId(_))))
      case _: CourseMetadata.$UnknownMember =>
        CourseService.Course.CourseMetadata.Empty
    }
  }

  def protoToCourier(keyed: CourseService.KeyedCourse): (String, Course) =
    keyed.courseId.id -> protoToCourier(keyed.course)

  def protoToCourier(course: CourseService.Course): Course =
    Course(
      instructorIds = course.instructorIds.map(_.id),
      partnerId = course.partnerId.id,
      slug = course.slug,
      name = course.name,
      description = course.description,
      extraData = Json.parse(course.extraData.toString).as[AnyData],
      courseMetadata = protoToCourier(course.courseMetadata)
    )

  def protoToCourier(
      courseMetadata: CourseService.Course.CourseMetadata): CourseMetadata = {
    courseMetadata match {
      case CourseService.Course.CourseMetadata.Certificate(member) =>
        CertificateCourseMetadata(
          member.certificateInstructorIds.toList.map(_.id))
      case CourseService.Course.CourseMetadata.Degree(member) =>
        DegreeCourseMetadata(member.degreeCertificateName,
                             member.degreeInstructorIds.map(_.id))
      case CourseService.Course.CourseMetadata.Empty =>
        CourseMetadata.$UnknownMember(new DataMap())
    }
  }

}
