package together.capstone2together.Controller;

import com.nimbusds.jose.shaded.json.JSONArray;
import com.nimbusds.jose.shaded.json.JSONObject;
import jakarta.persistence.NoResultException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.*;
import together.capstone2together.domain.*;
import together.capstone2together.dto.CreatorRoomDto;
import together.capstone2together.service.*;


import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/team-member") //팀원 모집 화면
public class TeamMemberController {

    private final RoomMemberService roomMemberService;

    private final MemberService memberService;
    private final RoomService roomService;
    private final SurveyAnswerService surveyAnswerService;

    private final SurveyService surveyService;
    private final QuestionService questionService;

    //==========================팀장탭=====================================

    //팀장 탭 - 팀장이 생성한 방 리스트
    @GetMapping("/creator")
    public JSONArray showCreatedRoom(HttpServletRequest request) {
        Member findOne = memberService.findById(request.getHeader("memberId"));
        return roomService.findCreatorRoomList(findOne);
    }

    //팀장 탭 - 팀장이 생성한 방에 모든 지원자가 들어온 경우 역할과 함께 뿌림
    @GetMapping("/creator/showJoined")
    public ResponseEntity<Object> showAll(HttpServletRequest request){ //Long은 RequestParam으로 안돼서 post로 맵핑

        Long roomId = Long.valueOf(request.getHeader("roomId"));
        Room findOne = roomService.findById(roomId);

        //리스트에 이미지 url도 끼워서 넣어야 함
        JSONObject object = new JSONObject();
        object.put("img",findOne.getItem().getImg());

        return ResponseEntity.ok(getObjectsByRoomCapacity(roomId, object));
    }
    @PostMapping("/creator/delete") //지원자가 0명인 경우 방 삭제 가능
    public ResponseEntity<String> deleteRoom(HttpServletRequest request){
        Long roomId = Long.valueOf(request.getHeader("roomId"));
        Room findRoom = roomService.findById(roomId);
        if(findRoom.getRoomMemberList().size()>0) return ResponseEntity.badRequest().body("지원자가 있는 방은 제거할 수 없습니다.");
        Survey findSurvey = findRoom.getSurvey();
        Question findQuestion = findSurvey.getQuestion();
        //방 삭제
        roomService.deleteRoom(findRoom);
        //설문 삭제
        surveyService.deleteSurvey(findSurvey);
        //질문 삭제
        questionService.deleteQuestion(findQuestion);

        return ResponseEntity.ok("방 삭제 완료");
    }

    private JSONArray getObjectsByRoomCapacity(Long roomId, JSONObject object) {
        //방에 인원이 다 차면 참여한 멤버들의 이름과 직책, 연락처를 리스트업
        if (roomService.checkCapacity(roomId)) //방에 지정한 인원만큼 다 차면
        {
            JSONArray findList = roomMemberService.findAllMemberInRoom(roomId);
            findList.add(object);
            return findList;

        } else //방에 지정한 인원이 차지 않으면 지원자 리스트로 올리기
        {
            JSONArray findList = surveyAnswerService.getAppliedMemberList(roomId); //지원자 아이디도 같이 넘김. 나중에 지원자 설문 답변 볼 때 이 아이디 프론트에게서 받을것
            findList.add(object);
            return findList;
        }
    }

    //이게 어떤 지원자를 눌렀는 지는 showAll에서 넘긴 지원자 아이디로 검색
    @GetMapping("/creator/showJoined/surveyAnswer") //팀장 탭 - 지원자 눌렀을 때 작성한 설문 답변 보기
    public JSONArray getSurveyAnswer(HttpServletRequest request){
        String appliedMemberId = request.getHeader("memberId"); //지원자 아이디
        Long roomId = Long.valueOf(request.getHeader("roomId"));
        return surveyAnswerService.findByMemberId(appliedMemberId,roomId);
    }
    @PostMapping("/creator/showJoined/surveyAnswer/pass")
    public ResponseEntity<String> surveyPass(HttpServletRequest request){ //팀장 탭 - 설문 답변 pass 판정 시키기
        Long surveyAnswerId = Long.valueOf(request.getHeader("surveyAnswerId"));
        SurveyAnswer findAnswer = surveyAnswerService.findById(surveyAnswerId);

        if(findAnswer.getStatus() == Status.WAITING)
            surveyAnswerService.setStatusToPass(surveyAnswerId);
        //지원자를 RoomMember로 등록
        //findAnswer = surveyAnswerService.findById(surveyAnswerId);
        if(findAnswer.getStatus()== Status.PASS) {
            RoomMember roomMember = RoomMember.create(findAnswer.getRoom(), findAnswer.getMember());
            roomMemberService.save(roomMember);
        }
        return ResponseEntity.ok("success");
    }
    @PostMapping("/creator/showJoined/surveyAnswer/fail")
    public ResponseEntity<String> surveyFail(HttpServletRequest request){ //팀장 탭 - 설문 답변 fail 판정 시키기
        Long surveyAnswerId = Long.valueOf(request.getHeader("surveyAnswerId"));
        SurveyAnswer findOne = surveyAnswerService.findById(surveyAnswerId);
        if(findOne.getStatus() == Status.PASS) throw new IllegalStateException("이미 PASS한 답변 입니다.");
        surveyAnswerService.setStatusToFail(surveyAnswerId);
        return ResponseEntity.ok("success");
    }

    //=====================================팀원 탭===================================================
    @GetMapping("/member")
    public JSONArray showJoinedRoom(HttpServletRequest request){ //팀원 탭 - 내가 지원한 방 목록들 보기
        Member findOne = memberService.findById(request.getHeader("memberId"));
        return surveyAnswerService.getJoinedRoomList(findOne);
    }
    @GetMapping("/member/showJoined") //팀원 탭 - 방에 인원이 다 차면 참여한 멤버들의 이름과 직책, 연락처를 리스트업. 안 차면 팀원 입장에서는 아무것도 안보이게?? 이거 물어보기
    public Object showAllByJoinedMember(HttpServletRequest request){
        Long roomId = Long.valueOf(request.getHeader("roomId"));
        String memberId = request.getHeader("memberId");
        Room findRoom = roomService.findById(roomId);
        Member findMember = memberService.findById(memberId);
        //그 방에 대해 pass 판정이 내린 사용자가 아니라면 에러
        if(!roomMemberService.trueJoinedMember(findMember, findRoom)) throw new NoResultException("해당 방에 PASS 판정을 받지 못했습니다.");
        //맞다면
        //리스트에 이미지 url도 끼워서 넣어야 함
        JSONObject object = new JSONObject();
        object.put("img",findRoom.getItem().getImg());
        
        return getObjectsByRoomCapacity(roomId, object);
    }

}
