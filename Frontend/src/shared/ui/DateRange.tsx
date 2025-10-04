import { DatePicker } from 'antd';
import type { RangePickerProps } from 'antd/es/date-picker';

const { RangePicker } = DatePicker;

interface DateRangeProps extends RangePickerProps {
  onChange?: RangePickerProps['onChange'];
}

const DateRange = ({ onChange, ...rest }: DateRangeProps) => (
  <RangePicker
    showTime
    allowClear
    onChange={onChange}
    style={{ minWidth: 280 }}
    {...rest}
  />
);

export default DateRange;
