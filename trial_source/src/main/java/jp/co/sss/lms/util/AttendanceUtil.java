package jp.co.sss.lms.util;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.mapper.MSectionMapper;

/**
 * 勤怠管理のユーティリティクラス
 * 
 * @author 東京ITスクール
 */
@Component
public class AttendanceUtil {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private MSectionMapper mSectionMapper;

	/**
	 * SSS定時・出退勤時間を元に、遅刻早退を判定をする
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @return 遅刻早退を判定メソッド
	 */
	public AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime) {
		return getStatus(trainingStartTime, trainingEndTime, Constants.SSS_WORK_START_TIME,
				Constants.SSS_WORK_END_TIME);
	}

	/**
	 * 与えられた定時・出退勤時間を元に、遅刻早退を判定する
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @param workStartTime     定時開始時刻
	 * @param workEndTime       定時終了時刻
	 * @return 判定結果
	 */
	private AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime, TrainingTime workStartTime, TrainingTime workEndTime) {
		// 定時が不明な場合、NONEを返却する
		if (workStartTime == null || workStartTime.isBlank() || workEndTime == null
				|| workEndTime.isBlank()) {
			return AttendanceStatusEnum.NONE;
		}
		boolean isLate = false, isEarly = false;
		// 定時より1分以上遅く出社していたら遅刻(＝はセーフ)
		if (trainingStartTime != null && trainingStartTime.isNotBlank()) {
			isLate = (trainingStartTime.compareTo(workStartTime) > 0);
		}
		// 定時より1分以上早く退社していたら早退(＝はセーフ)
		if (trainingEndTime != null && trainingEndTime.isNotBlank()) {
			isEarly = (trainingEndTime.compareTo(workEndTime) < 0);
		}
		if (isLate && isEarly) {
			return AttendanceStatusEnum.TARDY_AND_LEAVING_EARLY;
		}
		if (isLate) {
			return AttendanceStatusEnum.TARDY;
		}
		if (isEarly) {
			return AttendanceStatusEnum.LEAVING_EARLY;
		}
		return AttendanceStatusEnum.NONE;
	}

	/**
	 * 中抜け時間を時(hour)と分(minute)に変換
	 *
	 * @param min 中抜け時間
	 * @return 時(hour)と分(minute)に変換したクラス
	 */
	public TrainingTime calcBlankTime(int min) {
		int hour = min / 60;
		int minute = min % 60;
		TrainingTime total = new TrainingTime(hour, minute);
		return total;
	}

	/**
	 * 時刻分を丸めた本日日付を取得
	 * 
	 * @return "yyyy/M/d"形式の日付
	 */
	public Date getTrainingDate() {
		Date trainingDate;
		try {
			trainingDate = dateUtil.parse(dateUtil.toString(new Date()));
		} catch (ParseException e) {
			// DateUtil#toStringとparseは同様のフォーマットを使用しているため、起こりえないエラー
			throw new IllegalStateException();
		}
		return trainingDate;
	}

	/**
	 * 
	 * @return 15分刻みの時間(数値)と〇〇時〇〇分のマップ
	 */
	public LinkedHashMap<Integer, String> setBlankTime() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();

		// 画面では空白、実際はnullを選択できるように設定
		map.put(null, "");

		// 15分(i=15)から480分(8時間)未満まで15分刻みでループ
		for (int i = 15; i < 480; i += 15) {
			int hour = i / 60;
			int minute = i % 60;
			String time;

			// 設計書の「〇〇時〇〇分」という形式に合わせる
			// 0時の場合は「〇〇分」のみ、0分の場合は「〇〇時間」とする既存ロジックを整理
			if (hour == 0) {
				time = String.format("%02d分", minute);
			} else if (minute == 0) {
				time = String.format("%02d時間", hour);
			} else {
				// 〇〇時〇〇分
				time = String.format("%02d時%02d分", hour, minute);
			}

			// Key: 分（数値）, Value: 表示文字列
			map.put(i, time);
		}
		return map;
	}
	// 越川　Task26
	public LinkedHashMap<Integer, String> getHourMap() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();

		// 画面では空白、実際はnullを選択できるように設定
		map.put(null, "");

		// 0時から23時までループ
		for (int i = 0; i < 24; i++) {
			// 設計書：i+1 をして 01, 02...12 と表示させる場合（設計書の例 {0,"01"} に基づく）
			// もし 00, 01...11 が良ければ String.format("%02d", i) にしてください
			map.put(i, String.format("%02d", i));
		}

		return map;
	}

	public LinkedHashMap<Integer, String> getMinuteMap() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();

		map.put(null, "");

		// [loop] i=0; i<60; i++
		for (int i = 0; i < 60; i++) {
			map.put(i, String.format("%02d", i));
		}
		return map;
	}

	/**
	 * 研修日の判定
	 * 
	 * @param courseId
	 * @param trainingDate
	 * @return 判定結果
	 */
	public boolean isWorkDay(Integer courseId, Date trainingDate) {
		Integer count = mSectionMapper.getSectionCountByCourseId(courseId, trainingDate);
		if (count > 0) {
			return true;
		}
		return false;
	}

	/**
	 * No.008: 時間(時)の切り出し
	 * @param time "09:00" 形式の文字列
	 * @return Integerの時
	 */
	public Integer getHour(String timeString) {
		Integer e;

		if (timeString == null || timeString.length() < 2) {
			e = null;
			return e;
		} else {

			// 設計書：timeString.substring(0, 2)
			return Integer.parseInt(timeString.substring(0, 2));
		}
	}

	/**
	 * No.009: 時間(分)の切り出し
	 * @param time "09:00" 形式の文字列
	 * @return Integerの分
	 */
	public Integer getMinute(String timeString) {
		Integer f;
		if (timeString == null || timeString.length() < 5) {
			f = null;
			return f;
		}else {
			
//		 設計書："09:15" の 15 部分を抜き出す
		return Integer.parseInt(timeString.substring(3, 5));
	}
	}
}
