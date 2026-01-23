package jp.co.sss.lms.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.ParseException;
import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 *
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId, Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);

		//取得したデータを画面用に編集
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	//Task25 越川
	public boolean notEnterCheck(Integer lmsUserId) {

		//  現在日付を取得
		//sdf.formatでsdfで指定したyyyy-MM-ddにnew Date(2026年1月22日 18時51分30秒 500ミリ秒等）の細かい情報を流し込む。
		//sdf.formatでそぎ落とされた日付をDate型に戻す。今回はutilを使用した。
		//Date trainingDate = sdf.parse(sdf.format(new Date()));

		Date trainingDate = attendanceUtil.getTrainingDate();

		// Mapperを呼び出して、データベースから未入力件数を取得する
		//引数はDBで絞り込む条件
		int notEnterCount = tStudentAttendanceMapper.notEnterCount(
				lmsUserId,
				Constants.DB_FLG_FALSE,
				trainingDate);

		//  1件以上でtrue
		boolean showDialog = (notEnterCount > 0);

		return showDialog;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());

		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();

			dailyAttendanceForm.setStudentAttendanceId(dto.getStudentAttendanceId());
			dailyAttendanceForm.setTrainingDate(dateUtil.toString(dto.getTrainingDate()));

			// --- 設計書：時刻を「時」「分」に分割してセット ---
			// 出勤時間
			String trainingStartTime = dto.getTrainingStartTime();
			dailyAttendanceForm.setTrainingStartHour(attendanceUtil.getHour(trainingStartTime));
			dailyAttendanceForm.setTrainingStartMinute(attendanceUtil.getMinute(trainingStartTime));

			// 退勤時間
			String trainingEndTime = dto.getTrainingEndTime();
			dailyAttendanceForm.setTrainingEndHour(attendanceUtil.getHour(trainingEndTime));
			dailyAttendanceForm.setTrainingEndMinute(attendanceUtil.getMinute(trainingEndTime));

			// --- 既存の処理 ---
			if (dto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(dto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(dto.getBlankTime())));
			}

			dailyAttendanceForm.setStatus(String.valueOf(dto.getStatus()));
			dailyAttendanceForm.setNote(dto.getNote());
			dailyAttendanceForm.setSectionName(dto.getSectionName());
			dailyAttendanceForm.setIsToday(dto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil.dateToString(dto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(dto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}
		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		// 更新・登録用のリストを別途用意（元のリストを直接addするとループ中に不具合が出る可能性があるため）
		List<TStudentAttendance> upsertList = new ArrayList<>();

		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();

			// 既存データの特定
			Date trainingDate = null;
			try {
				trainingDate = dateUtil.parse(dailyAttendanceForm.getTrainingDate());
			} catch (java.text.ParseException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(trainingDate)) {
					tStudentAttendance = entity;
					break;
				}
			}

			// 基本情報のセット
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setTrainingDate(trainingDate);

			// 出勤時間の結合
			String formattedStartTime = null;
			if (dailyAttendanceForm.getTrainingStartHour() != null
					&& dailyAttendanceForm.getTrainingStartMinute() != null) {
				// "HH:mm" 形式に結合
				formattedStartTime = String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingStartHour(),
						dailyAttendanceForm.getTrainingStartMinute());
			}
			tStudentAttendance.setTrainingStartTime(formattedStartTime);

			//退勤時間の結合
			String formattedEndTime = null;
			if (dailyAttendanceForm.getTrainingEndHour() != null
					&& dailyAttendanceForm.getTrainingEndMinute() != null) {
				// "HH:mm" 形式に結合
				formattedEndTime = String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingEndHour(),
						dailyAttendanceForm.getTrainingEndMinute());
			}
			tStudentAttendance.setTrainingEndTime(formattedEndTime);

			// 遅刻早退ステータスの判定用（TrainingTimeクラスの引数に結合後の文字列を渡す）
			TrainingTime trainingStartTime = (formattedStartTime != null) ? new TrainingTime(formattedStartTime) : null;
			TrainingTime trainingEndTime = (formattedEndTime != null) ? new TrainingTime(formattedEndTime) : null;

			// ステータス判定
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !"欠席".equals(dailyAttendanceForm.getStatusDispName())) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}

			//中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			//備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			//更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			//削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			//登録用Listへ追加
			upsertList.add(tStudentAttendance);
		}

		// 登録・更新処理の実行
		for (TStudentAttendance entity : upsertList) {
			if (entity.getStudentAttendanceId() == null) {
				entity.setFirstCreateUser(loginUserDto.getLmsUserId());
				entity.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(entity);
			} else {
				tStudentAttendanceMapper.update(entity);
			}
		}

		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}
}
