import React from 'react'
import { shallow } from 'enzyme'
import TPMainContainer from '.'

it('should render correctly with no props', () => {
  const wrapper = shallow(<TPMainContainer><React.Fragment /></TPMainContainer>)

  expect(wrapper).toBeDefined()
  //   expect(wrapper).toMatchSnapshot()
})